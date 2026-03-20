package com.autogradingsystem.multiassessment;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * GradingProgressTracker - Thread-safe progress tracker for multi-assessment grading.
 *
 * Each assessment has 5 phases. Progress per assessment = phases_done / 5.
 * Overall progress = sum of all assessment progress / total assessments.
 *
 * Singleton held by AssessmentOrchestrator — reset at the start of each gradeAll() call.
 */
public class GradingProgressTracker {

    public static final int TOTAL_PHASES = 5;

    public enum Phase {
        EXTRACTION   (1, "Extracting submissions"),
        DISCOVERY    (2, "Building grading plan"),
        EXECUTION    (3, "Grading student code"),
        PLAGIARISM   (4, "Checking plagiarism"),
        ANALYSIS     (5, "Exporting reports");

        public final int number;
        public final String label;
        Phase(int n, String l) { this.number = n; this.label = l; }
    }

    // ── State ──────────────────────────────────────────────────────────────

    private final Map<String, Integer>  phaseDone    = new ConcurrentHashMap<>();
    private final Map<String, Phase>    currentPhase = new ConcurrentHashMap<>();
    private final Map<String, Boolean>  completed    = new ConcurrentHashMap<>();
    private final Map<String, Boolean>  failed       = new ConcurrentHashMap<>();
    private final List<String>          assessmentNames = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean            running      = false;
    private volatile boolean            done         = false;
    private volatile long               startTime    = 0;

    // ── Lifecycle ──────────────────────────────────────────────────────────

    public void reset(List<String> names) {
        phaseDone.clear();
        currentPhase.clear();
        completed.clear();
        failed.clear();
        assessmentNames.clear();
        assessmentNames.addAll(names);
        for (String n : names) {
            phaseDone.put(n, 0);
        }
        running   = true;
        done      = false;
        startTime = System.currentTimeMillis();
    }

    public void markPhaseStarted(String assessment, Phase phase) {
        currentPhase.put(assessment, phase);
    }

    public void markPhaseComplete(String assessment, Phase phase) {
        phaseDone.merge(assessment, 1, Integer::sum);
        currentPhase.put(assessment, phase);
    }

    public void markAssessmentComplete(String assessment) {
        phaseDone.put(assessment, TOTAL_PHASES);
        completed.put(assessment, true);
    }

    public void markAssessmentFailed(String assessment) {
        failed.put(assessment, true);
        completed.put(assessment, true);
    }

    public void markAllDone() {
        running = false;
        done    = true;
    }

    // ── Snapshot for SSE ──────────────────────────────────────────────────

    public ProgressSnapshot snapshot() {
        List<AssessmentProgress> list = new ArrayList<>();
        for (String name : assessmentNames) {
            int phases       = phaseDone.getOrDefault(name, 0);
            Phase cur        = currentPhase.get(name);
            boolean isOk     = Boolean.TRUE.equals(completed.get(name)) && !Boolean.TRUE.equals(failed.get(name));
            boolean isFailed = Boolean.TRUE.equals(failed.get(name));
            String phaseLabel = cur != null ? cur.label : "Waiting...";
            int pct = (int) Math.round(phases * 100.0 / TOTAL_PHASES);
            list.add(new AssessmentProgress(name, pct, phaseLabel, isOk, isFailed));
        }

        int total = list.stream().mapToInt(a -> a.percent).sum();
        int overall = list.isEmpty() ? 0 : total / list.size();
        long elapsed = startTime > 0 ? (System.currentTimeMillis() - startTime) / 1000 : 0;

        return new ProgressSnapshot(list, overall, done, elapsed);
    }

    // ── DTOs ──────────────────────────────────────────────────────────────

    public record AssessmentProgress(String name, int percent, String currentPhase,
                                     boolean completed, boolean failed) {}

    public record ProgressSnapshot(List<AssessmentProgress> assessments,
                                   int overallPercent, boolean done, long elapsedSeconds) {}
}