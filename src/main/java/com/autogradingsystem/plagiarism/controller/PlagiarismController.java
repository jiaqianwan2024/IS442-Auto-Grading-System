package com.autogradingsystem.plagiarism.controller;

import com.autogradingsystem.PathConfig;
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

/**
 * PlagiarismController
 *
 * Entry-point for the plagiarism microservice.
 *
 * CHANGES IN v2.8 (multi-assessment):
 * - Added path-aware constructor accepting outputExtracted path.
 * - resolveOutputExtracted() falls back to PathConfig when path is null.
 *
 * @author IS442 Team
 * @version 2.8
 */
public class PlagiarismController {

    private final PlagiarismDetector       detector;
    private final PlagiarismReportExporter exporter;
    private final PlagiarismConfig         config;

    // ================================================================
    // PATH FIELDS — null means "use global PathConfig" (single-assessment)
    // ================================================================

    private final Path outputExtracted;

    // ================================================================
    // CONSTRUCTORS
    // ================================================================

    /** Default constructor — uses PlagiarismConfig defaults and global PathConfig. */
    public PlagiarismController() {
        this(new PlagiarismConfig(), null);
    }

    /**
     * Constructor for custom thresholds — uses global PathConfig paths.
     *
     * @param config Custom plagiarism configuration
     */
    public PlagiarismController(PlagiarismConfig config) {
        this(config, null);
    }

    /**
     * Path-aware constructor for multi-assessment support.
     * Called by AssessmentOrchestrator with per-assessment isolated paths.
     *
     * @param outputExtracted Path to the extracted students directory for this assessment
     */
    public PlagiarismController(Path outputExtracted) {
        this(new PlagiarismConfig(), outputExtracted);
    }

    /**
     * Full constructor — custom config + explicit path.
     *
     * @param config          Custom plagiarism configuration
     * @param outputExtracted Path to the extracted students directory (null = use PathConfig)
     */
    public PlagiarismController(PlagiarismConfig config, Path outputExtracted) {
        this.config          = config;
        this.detector        = new PlagiarismDetector();
        this.exporter        = new PlagiarismReportExporter();
        this.outputExtracted = outputExtracted;
    }

    // ================================================================
    // PATH RESOLUTION
    // ================================================================

    private Path resolveOutputExtracted() {
        return outputExtracted != null ? outputExtracted : PathConfig.OUTPUT_EXTRACTED;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Runs the full plagiarism detection pipeline and exports the report.
     *
     * @param gradingPlan The GradingPlan produced by DiscoveryController
     * @return PlagiarismSummary with flagged pair counts and report path
     */
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
            reportPath = exporter.export(results);
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

    // ── Inner class: PlagiarismSummary ────────────────────────────────────────

    public static class PlagiarismSummary {

        public final List<PlagiarismResult>   allResults;
        public final List<PlagiarismResult>   flaggedResults;
        public final Map<String, Set<String>> flaggedStudents;
        public final Path                     reportPath;

        public PlagiarismSummary(List<PlagiarismResult> allResults,
                                  List<PlagiarismResult> flaggedResults,
                                  Map<String, Set<String>> flaggedStudents,
                                  Path reportPath) {
            this.allResults      = Collections.unmodifiableList(allResults);
            this.flaggedResults  = Collections.unmodifiableList(flaggedResults);
            this.flaggedStudents = flaggedStudents;
            this.reportPath      = reportPath;
        }

        public static PlagiarismSummary empty() {
            return new PlagiarismSummary(
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyMap(),
                    null);
        }

        public boolean hasSuspiciousPairs()  { return !flaggedResults.isEmpty(); }
        public int     getFlaggedPairCount() { return flaggedResults.size(); }

        public boolean isStudentFlagged(String studentId) {
            return flaggedStudents.containsKey(studentId);
        }
    }
}