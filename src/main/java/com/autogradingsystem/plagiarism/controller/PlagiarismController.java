package com.autogradingsystem.plagiarism.controller;

import com.autogradingsystem.model.GradingPlan;
import com.autogradingsystem.plagiarism.model.PlagiarismConfig;
import com.autogradingsystem.plagiarism.model.PlagiarismResult;
import com.autogradingsystem.plagiarism.service.PlagiarismDetector;
import com.autogradingsystem.plagiarism.service.PlagiarismReportExporter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class PlagiarismController {

    private final PlagiarismDetector detector;
    private final PlagiarismReportExporter exporter;
    private final PlagiarismConfig config;
    private final Path outputExtracted;
    private final Path outputReports;
    private final String assessmentTitle;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Path-aware: extracted + reports (no title) */
    public PlagiarismController(Path outputExtracted, Path outputReports) {
        this(new PlagiarismConfig(), outputExtracted, outputReports, null);
    }

    /** Path-aware: extracted + reports + assessment title */
    public PlagiarismController(Path outputExtracted, Path outputReports, String assessmentTitle) {
        this(new PlagiarismConfig(), outputExtracted, outputReports, assessmentTitle);
    }

    /** Full constructor (no title) */
    public PlagiarismController(PlagiarismConfig config, Path outputExtracted, Path outputReports) {
        this(config, outputExtracted, outputReports, null);
    }

    /** Full constructor — everything delegates here */
    public PlagiarismController(PlagiarismConfig config, Path outputExtracted, Path outputReports,
                                String assessmentTitle) {
        this.config = config;
        this.detector = new PlagiarismDetector();
        this.exporter = new PlagiarismReportExporter();
        this.outputExtracted = outputExtracted;
        this.outputReports = outputReports;
        this.assessmentTitle = assessmentTitle;
    }

    // ── Path resolution ───────────────────────────────────────────────────────

    private Path resolveOutputExtracted() { return outputExtracted; }
    private Path resolveOutputReports() { return outputReports; }

    // ── Public API ────────────────────────────────────────────────────────────

    public PlagiarismSummary runPlagiarismCheck(GradingPlan gradingPlan) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("🔍 PLAGIARISM DETECTION");
        System.out.println("=".repeat(70));

        if (gradingPlan == null || gradingPlan.isEmpty()) {
            System.out.println("⚠️  Empty grading plan — skipping plagiarism check.");
            return PlagiarismSummary.empty();
        }

        List<PlagiarismResult> results;
        try {
            results = detector.detect(resolveOutputExtracted(),
                    gradingPlan.getTasks(),
                    config);
        } catch (IOException e) {
            System.out.println("❌ Plagiarism detection failed: " + e.getMessage());
            return PlagiarismSummary.empty();
        }

        Path reportPath = null;
        try {
            reportPath = exporter.export(results, resolveOutputReports(), assessmentTitle); // ← KEY FIX
            System.out.println("✅ Plagiarism Report → " + reportPath.toAbsolutePath());
        } catch (IOException e) {
            System.out.println("❌ Plagiarism report export failed: " + e.getMessage());
        }

        List<PlagiarismResult> flagged = results.stream()
                .filter(PlagiarismResult::isFlagged)
                .collect(Collectors.toList());

        Map<String, Set<String>> flaggedStudents = buildFlaggedStudentMap(flagged);

        if (flagged.isEmpty()) {
            System.out.println("✅ No suspicious pairs detected.");
        } else {
            System.out.println("🚨 " + flagged.size() + " suspicious pair(s) found:");
            flagged.forEach(r -> System.out.println(
                    "   • " + r.getStudentA() + " ↔ " + r.getStudentB()
                            + "  [" + r.getQuestionId() + "]  "
                            + String.format("%.1f%%", r.getSimilarityPercent())));
        }

        System.out.println("=".repeat(70));
        return new PlagiarismSummary(results, flagged, flaggedStudents, reportPath);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private Map<String, Set<String>> buildFlaggedStudentMap(List<PlagiarismResult> flagged) {
        Map<String, Set<String>> map = new java.util.LinkedHashMap<>();
        for (PlagiarismResult r : flagged) {
            map.computeIfAbsent(r.getStudentA(), k -> new java.util.LinkedHashSet<>())
                    .add(r.getQuestionId());
            map.computeIfAbsent(r.getStudentB(), k -> new java.util.LinkedHashSet<>())
                    .add(r.getQuestionId());
        }
        return Collections.unmodifiableMap(map);
    }

    // ── PlagiarismSummary (unchanged) ─────────────────────────────────────────

    public static class PlagiarismSummary {
        public final List<PlagiarismResult> allResults;
        public final List<PlagiarismResult> flaggedResults;
        public final Map<String, Set<String>> flaggedStudents;
        public final Path reportPath;

        public PlagiarismSummary(List<PlagiarismResult> allResults,
                List<PlagiarismResult> flaggedResults,
                Map<String, Set<String>> flaggedStudents,
                Path reportPath) {
            this.allResults = Collections.unmodifiableList(allResults);
            this.flaggedResults = Collections.unmodifiableList(flaggedResults);
            this.flaggedStudents = flaggedStudents;
            this.reportPath = reportPath;
        }

        public static PlagiarismSummary empty() {
            return new PlagiarismSummary(Collections.emptyList(),
                    Collections.emptyList(), Collections.emptyMap(), null);
        }

        public boolean hasSuspiciousPairs() {
            return !flaggedResults.isEmpty();
        }

        public int getFlaggedPairCount() {
            return flaggedResults.size();
        }

        public boolean isStudentFlagged(String studentId) {
            return flaggedStudents.containsKey(studentId);
        }
    }
}
