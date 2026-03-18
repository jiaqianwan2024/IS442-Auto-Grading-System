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
 * RESPONSIBILITIES:
 *   - Accept a GradingPlan (already built by DiscoveryController)
 *   - Configure PlagiarismConfig (defaults or custom thresholds)
 *   - Delegate detection to PlagiarismDetector
 *   - Export the XLSX report via PlagiarismReportExporter
 *   - Return a PlagiarismSummary for the web layer / GradingService
 *
 * INTEGRATION POINT:
 *   Add the following call to GradingService.runFullPipeline() after step 5
 *   (after ExecutionController.gradeAllStudents):
 *
 *       PlagiarismController plagController = new PlagiarismController();
 *       PlagiarismController.PlagiarismSummary plagSummary =
 *               plagController.runPlagiarismCheck(gradingPlan);
 *
 *   The summary's flaggedPairs map can be passed to AnalysisController if
 *   you wish to annotate the score sheet with plagiarism flags.
 */
public class PlagiarismController {

    private final PlagiarismDetector       detector;
    private final PlagiarismReportExporter exporter;
    private final PlagiarismConfig         config;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Default constructor — uses PlagiarismConfig defaults. */
    public PlagiarismController() {
        this(new PlagiarismConfig());
    }

    /**
     * Constructor for custom thresholds.
     *
     * @param config Custom plagiarism configuration
     */
    public PlagiarismController(PlagiarismConfig config) {
        this.config   = config;
        this.detector = new PlagiarismDetector();
        this.exporter = new PlagiarismReportExporter();
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

        // ── Step 1: Run detection ─────────────────────────────────────────────
        List<PlagiarismResult> results;
        try {
            results = detector.detect(PathConfig.OUTPUT_EXTRACTED,
                                      gradingPlan.getTasks(),
                                      config);
        } catch (IOException e) {
            System.out.println("❌ Plagiarism detection failed: " + e.getMessage());
            return PlagiarismSummary.empty();
        }

        // ── Step 2: Export report ─────────────────────────────────────────────
        Path reportPath = null;
        try {
            reportPath = exporter.export(results);
            System.out.println("✅ Plagiarism Report → " + reportPath.toAbsolutePath());
        } catch (IOException e) {
            System.out.println("❌ Plagiarism report export failed: " + e.getMessage());
        }

        // ── Step 3: Build summary ─────────────────────────────────────────────
        List<PlagiarismResult> flagged = results.stream()
                .filter(PlagiarismResult::isFlagged)
                .collect(Collectors.toList());

        // Per-student flag set: student → set of questions where they were flagged
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

    /**
     * Immutable summary returned to the calling layer (GradingService / web layer).
     *
     * Contains:
     *   - allResults     : Every pair checked (for rendering in the web UI)
     *   - flaggedResults : Only the suspicious pairs
     *   - flaggedStudents: student → set of question IDs where they were flagged
     *   - reportPath     : Where the XLSX was written (may be null on export failure)
     */
    public static class PlagiarismSummary {

        public final List<PlagiarismResult>       allResults;
        public final List<PlagiarismResult>       flaggedResults;
        public final Map<String, Set<String>>     flaggedStudents;
        public final Path                         reportPath;

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

        public boolean hasSuspiciousPairs() {
            return !flaggedResults.isEmpty();
        }

        public int getFlaggedPairCount() {
            return flaggedResults.size();
        }

        /**
         * Returns true if a specific student was flagged in any question.
         * Useful for adding remarks in the score sheet.
         */
        public boolean isStudentFlagged(String studentId) {
            return flaggedStudents.containsKey(studentId);
        }
    }
}
