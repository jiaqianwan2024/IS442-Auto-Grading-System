package com.autogradingsystem.multiassessment;

import com.autogradingsystem.analysis.controller.AnalysisController;
import com.autogradingsystem.discovery.controller.DiscoveryController;
import com.autogradingsystem.execution.controller.ExecutionController;
import com.autogradingsystem.extraction.controller.ExtractionController;
import com.autogradingsystem.model.GradingPlan;
import com.autogradingsystem.model.GradingResult;
import com.autogradingsystem.model.Student;
import com.autogradingsystem.plagiarism.controller.PlagiarismController;
import com.autogradingsystem.plagiarism.model.PlagiarismResult;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * AssessmentOrchestrator - Concurrent Multi-Assessment Pipeline Runner
 *
 * PURPOSE:
 *   Accepts a list of AssessmentBundles (one per assessment the professor
 *   wants to grade) and runs the full grading pipeline for each one
 *   concurrently using a fixed thread pool. Collects all results into a
 *   MultiAssessmentReport that contains:
 *     - Per-assessment results and logs
 *     - A combined cross-assessment summary
 *
 * PIPELINE PER ASSESSMENT (same as GradingService.runFullPipeline):
 *   Phase 1 — Extraction   (ExtractionController)
 *   Phase 2 — Discovery    (DiscoveryController)
 *   Phase 3 — Execution    (ExecutionController)
 *   Phase 4 — Plagiarism   (PlagiarismController)
 *   Phase 5 — Analysis     (AnalysisController)
 *
 * CONCURRENCY:
 *   - Each assessment runs in its own Callable on the thread pool.
 *   - Thread pool size = min(assessmentCount, MAX_CONCURRENT_ASSESSMENTS).
 *   - Each assessment uses its own AssessmentPathConfig so no file-system
 *     collisions can occur between concurrent runs.
 *   - The orchestrator waits for ALL assessments to finish before returning
 *     the combined report, regardless of individual failures.
 *
 * ERROR ISOLATION:
 *   A failure in one assessment's pipeline does NOT cancel the others.
 *   Each assessment's result carries a success flag and error message so
 *   the caller can report partial success.
 *
 * @author IS442 Team
 * @version 1.0 (Multi-Assessment Bonus Feature)
 */
@Service
public class AssessmentOrchestrator {

    // ================================================================
    // CONSTANTS
    // ================================================================

    /**
     * Maximum number of assessments that run at the same time.
     * Tuned to avoid overwhelming the JVM with too many javac subprocesses.
     * Each concurrent assessment can spawn N student × M question threads
     * via ExecutionController, so keep this low.
     */
    private static final int MAX_CONCURRENT_ASSESSMENTS = 4;

    // Shared progress tracker — reset on every gradeAll() call
    private final GradingProgressTracker tracker = new GradingProgressTracker();

    public GradingProgressTracker getTracker() { return tracker; }

    // ================================================================
    // PUBLIC API
    // ================================================================

    /**
     * Grades all provided assessments concurrently and returns a combined report.
     *
     * STEPS:
     * 1. Validate every bundle has required files; reject invalid ones early.
     * 2. Submit each valid bundle as a Callable to the thread pool.
     * 3. Wait for all futures to complete (no timeout — grading can be slow).
     * 4. Collect individual reports and build the combined summary.
     *
     * @param bundles List of assessment bundles to grade. Must not be null or empty.
     * @return MultiAssessmentReport containing all individual reports + summary
     */
    public MultiAssessmentReport gradeAll(List<AssessmentBundle> bundles) {
        if (bundles == null || bundles.isEmpty()) {
            return MultiAssessmentReport.empty("No assessments provided.");
        }

        System.out.println("\n╔══════════════════════════════════════════════════════════╗");
        System.out.println("║  MULTI-ASSESSMENT GRADING — " + bundles.size() + " assessment(s)             ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝\n");

        // ── 1. Validate all bundles upfront ─────────────────────────────────
        List<AssessmentBundle> validBundles   = new ArrayList<>();
        List<String>           validationLogs = new ArrayList<>();

        for (AssessmentBundle bundle : bundles) {
            if (bundle.isReady()) {
                validBundles.add(bundle);
                validationLogs.add("✅ [" + bundle.getAssessmentName() + "] — ready");
            } else {
                validationLogs.add("❌ [" + bundle.getAssessmentName()
                        + "] — missing input files, skipped");
                System.err.println("⚠  Skipping assessment '" + bundle.getAssessmentName()
                        + "' — missing required input files.");
            }
        }

        if (validBundles.isEmpty()) {
            return MultiAssessmentReport.empty("All assessments failed validation: "
                    + String.join(", ", validationLogs));
        }

        // Reset tracker with names of valid assessments
        tracker.reset(validBundles.stream()
                .map(AssessmentBundle::getAssessmentName)
                .collect(java.util.stream.Collectors.toList()));

        // ── 2. Build thread pool and submit tasks ────────────────────────────
        int poolSize = Math.min(validBundles.size(), MAX_CONCURRENT_ASSESSMENTS);
        ExecutorService pool = Executors.newFixedThreadPool(poolSize);

        List<Future<AssessmentReport>> futures = new ArrayList<>();
        for (AssessmentBundle bundle : validBundles) {
            futures.add(pool.submit(() -> runSinglePipeline(bundle)));
        }

        pool.shutdown();

        // ── 3. Collect results — wait for all ────────────────────────────────
        List<AssessmentReport> individualReports = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            AssessmentBundle bundle = validBundles.get(i);
            try {
                AssessmentReport report = futures.get(i).get(); // blocks until done
                individualReports.add(report);
            } catch (ExecutionException e) {
                // Pipeline threw an unexpected uncaught exception
                individualReports.add(AssessmentReport.failed(
                        bundle.getAssessmentName(),
                        "Unexpected error: " + e.getCause().getMessage()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                individualReports.add(AssessmentReport.failed(
                        bundle.getAssessmentName(), "Interrupted"));
            }
        }

        tracker.markAllDone();

        // ── 4. Build combined report ──────────────────────────────────────────
        MultiAssessmentReport combined = buildCombinedReport(individualReports, validationLogs);

        printSummaryTable(combined);

        return combined;
    }

    // ================================================================
    // SINGLE-ASSESSMENT PIPELINE
    // ================================================================

    /**
     * Runs the full grading pipeline for ONE assessment in an isolated context.
     * This method is called on a worker thread from the pool.
     *
     * It mirrors GradingService.runFullPipeline() exactly, but uses
     * the bundle's AssessmentPathConfig instead of the global PathConfig statics.
     *
     * @param bundle The assessment to grade
     * @return AssessmentReport with results and logs for this assessment
     */
    private AssessmentReport runSinglePipeline(AssessmentBundle bundle) {
        String name                = bundle.getAssessmentName();
        AssessmentPathConfig paths = bundle.getPathConfig();
        List<String> logs          = new ArrayList<>();
        String prefix              = "[" + name + "] ";

        System.out.println(prefix + "▶  Starting pipeline...");

        try {
            paths.ensureDirectories();

            // ── Phase 1: Extraction ──────────────────────────────────────────
            tracker.markPhaseStarted(name, GradingProgressTracker.Phase.EXTRACTION);
            logs.add(prefix + "Phase 1 — Extraction");
            ExtractionController extraction = new ExtractionController(
                    paths.CSV_SCORESHEET,
                    paths.INPUT_SUBMISSIONS,
                    paths.OUTPUT_EXTRACTED);

            int studentCount = extraction.extractAndValidate();
            tracker.markPhaseComplete(name, GradingProgressTracker.Phase.EXTRACTION);
            logs.add(prefix + "✅ Extracted " + studentCount + " student(s)");

            // ── Phase 2: Discovery ───────────────────────────────────────────
            tracker.markPhaseStarted(name, GradingProgressTracker.Phase.DISCOVERY);
            logs.add(prefix + "Phase 2 — Discovery");
            DiscoveryController discovery = new DiscoveryController(
                    paths.INPUT_TEMPLATE,
                    paths.INPUT_TESTERS);

            GradingPlan gradingPlan = discovery.buildGradingPlan();
            tracker.markPhaseComplete(name, GradingProgressTracker.Phase.DISCOVERY);
            logs.add(prefix + "✅ Grading plan: " + gradingPlan.getTaskCount() + " task(s)");

            // ── Phase 3: Execution ───────────────────────────────────────────
            tracker.markPhaseStarted(name, GradingProgressTracker.Phase.EXECUTION);
            logs.add(prefix + "Phase 3 — Execution");
            ExecutionController execution = new ExecutionController(
                    paths.OUTPUT_EXTRACTED, paths.CSV_SCORESHEET,
                    paths.INPUT_TESTERS, paths.INPUT_TEMPLATE);

            List<GradingResult>  results      = execution.gradeAllStudents(gradingPlan);
            Map<String, String>  remarks      = execution.getRemarksByStudent();
            Map<String, String>  anomalyRmks  = execution.getAnomalyRemarksByStudent();
            List<Student>        allStudents  = execution.getLastGradedStudents();
            tracker.markPhaseComplete(name, GradingProgressTracker.Phase.EXECUTION);
            logs.add(prefix + "✅ Graded " + results.size() + " result(s)");

            // ── Phase 4: Plagiarism ──────────────────────────────────────────
            tracker.markPhaseStarted(name, GradingProgressTracker.Phase.PLAGIARISM);
            logs.add(prefix + "Phase 4 — Plagiarism detection");
            PlagiarismController plagController = new PlagiarismController(paths.OUTPUT_EXTRACTED);
            PlagiarismController.PlagiarismSummary plagSummary =
                    plagController.runPlagiarismCheck(gradingPlan);

            Map<String, String> plagiarismNotes = buildPlagiarismNotes(plagSummary);

            if (plagSummary.hasSuspiciousPairs()) {
                logs.add(prefix + "🚨 Plagiarism: "
                        + plagSummary.getFlaggedPairCount() + " suspicious pair(s)");
            } else {
                logs.add(prefix + "✅ Plagiarism: no suspicious pairs detected");
            }
            tracker.markPhaseComplete(name, GradingProgressTracker.Phase.PLAGIARISM);

            // ── Phase 5: Analysis & export ───────────────────────────────────
            tracker.markPhaseStarted(name, GradingProgressTracker.Phase.ANALYSIS);
            logs.add(prefix + "Phase 5 — Analysis & export");
            AnalysisController analysis = new AnalysisController(
                    paths.CSV_SCORESHEET,
                    paths.OUTPUT_REPORTS,
                    paths.INPUT_TESTERS);

            analysis.analyzeAndDisplay(results, remarks, anomalyRmks, allStudents,
                    plagiarismNotes);
            tracker.markPhaseComplete(name, GradingProgressTracker.Phase.ANALYSIS);
            tracker.markAssessmentComplete(name);
            logs.add(prefix + "✅ Reports exported to " + paths.OUTPUT_REPORTS);

            System.out.println(prefix + "✅ Pipeline complete — " + studentCount
                    + " student(s), " + results.size() + " result(s)");

            return new AssessmentReport(name, true, studentCount, results, logs, null);

        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            logs.add(prefix + "❌ Pipeline failed: " + errorMsg);
            System.err.println(prefix + "❌ Pipeline failed: " + errorMsg);
            e.printStackTrace();
            tracker.markAssessmentFailed(name);
            return AssessmentReport.failed(name, errorMsg, logs);
        }
    }

    // ================================================================
    // REPORT BUILDERS
    // ================================================================

    /**
     * Aggregates individual assessment reports into a combined summary.
     */
    private MultiAssessmentReport buildCombinedReport(
            List<AssessmentReport> reports, List<String> validationLogs) {

        int totalStudents   = 0;
        int totalResults    = 0;
        int successCount    = 0;
        int failCount       = 0;
        List<String> allLogs = new ArrayList<>(validationLogs);

        for (AssessmentReport r : reports) {
            totalStudents += r.getStudentCount();
            totalResults  += r.getResults().size();
            if (r.isSuccess()) successCount++; else failCount++;
            allLogs.addAll(r.getLogs());
        }

        return new MultiAssessmentReport(
                reports,
                allLogs,
                totalStudents,
                totalResults,
                successCount,
                failCount);
    }

    /**
     * Prints a compact summary table to stdout after all assessments finish.
     */
    private void printSummaryTable(MultiAssessmentReport report) {
        System.out.println("\n╔══════════════════════════════════════════════════════════╗");
        System.out.println("║  MULTI-ASSESSMENT SUMMARY                                ║");
        System.out.println("╠══════════════════════════════════════════════════════════╣");
        System.out.printf("║  %-20s  %-10s  %-10s  %-8s  ║%n",
                "Assessment", "Students", "Results", "Status");
        System.out.println("╠══════════════════════════════════════════════════════════╣");

        for (AssessmentReport r : report.getIndividualReports()) {
            String status = r.isSuccess() ? "✅ OK" : "❌ FAILED";
            System.out.printf("║  %-20s  %-10d  %-10d  %-8s  ║%n",
                    truncate(r.getAssessmentName(), 20),
                    r.getStudentCount(),
                    r.getResults().size(),
                    status);
        }

        System.out.println("╠══════════════════════════════════════════════════════════╣");
        System.out.printf("║  %-20s  %-10d  %-10d  %d ok / %d fail   ║%n",
                "TOTAL",
                report.getTotalStudents(),
                report.getTotalResults(),
                report.getSuccessCount(),
                report.getFailCount());
        System.out.println("╚══════════════════════════════════════════════════════════╝\n");
    }

    private String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 1) + "…";
    }

    // ================================================================
    // PLAGIARISM NOTES BUILDER (same logic as GradingService)
    // ================================================================

    private Map<String, String> buildPlagiarismNotes(
            PlagiarismController.PlagiarismSummary plagSummary) {

        Map<String, Map<String, List<String>>> perStudentPerQuestion = new LinkedHashMap<>();

        for (PlagiarismResult r : plagSummary.flaggedResults) {
            String a   = r.getStudentA();
            String b   = r.getStudentB();
            String qid = r.getQuestionId();
            String pct = String.format("%.1f%%", r.getSimilarityPercent());

            perStudentPerQuestion
                .computeIfAbsent(a, k -> new LinkedHashMap<>())
                .computeIfAbsent(qid, k -> new ArrayList<>())
                .add("with " + b + " (" + pct + ")");

            perStudentPerQuestion
                .computeIfAbsent(b, k -> new LinkedHashMap<>())
                .computeIfAbsent(qid, k -> new ArrayList<>())
                .add("with " + a + " (" + pct + ")");
        }

        Map<String, String> notes = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, List<String>>> studentEntry
                : perStudentPerQuestion.entrySet()) {
            List<String> parts = new ArrayList<>();
            for (Map.Entry<String, List<String>> qEntry : studentEntry.getValue().entrySet()) {
                parts.add(qEntry.getKey() + ": flagged " + String.join(", ", qEntry.getValue()));
            }
            notes.put(studentEntry.getKey(), String.join("; ", parts));
        }

        return notes;
    }

    // ================================================================
    // INNER CLASSES — Report Models
    // ================================================================

    /**
     * Result of grading one assessment through the full pipeline.
     */
    public static class AssessmentReport {
        private final String             assessmentName;
        private final boolean            success;
        private final int                studentCount;
        private final List<GradingResult> results;
        private final List<String>        logs;
        private final String             errorMessage;

        public AssessmentReport(String assessmentName, boolean success,
                                int studentCount, List<GradingResult> results,
                                List<String> logs, String errorMessage) {
            this.assessmentName = assessmentName;
            this.success        = success;
            this.studentCount   = studentCount;
            this.results        = results != null ? results : Collections.emptyList();
            this.logs           = logs    != null ? logs    : Collections.emptyList();
            this.errorMessage   = errorMessage;
        }

        /** Creates a failed report with a short error message and no results. */
        public static AssessmentReport failed(String name, String error) {
            return new AssessmentReport(name, false, 0, Collections.emptyList(),
                    List.of("[" + name + "] ❌ " + error), error);
        }

        /** Creates a failed report with pre-accumulated logs. */
        public static AssessmentReport failed(String name, String error, List<String> logs) {
            return new AssessmentReport(name, false, 0, Collections.emptyList(), logs, error);
        }

        public String              getAssessmentName() { return assessmentName; }
        public boolean             isSuccess()         { return success; }
        public int                 getStudentCount()   { return studentCount; }
        public List<GradingResult> getResults()        { return results; }
        public List<String>        getLogs()           { return logs; }
        public String              getErrorMessage()   { return errorMessage; }
    }

    /**
     * Combined report across all assessments run in one orchestration call.
     */
    public static class MultiAssessmentReport {
        private final List<AssessmentReport> individualReports;
        private final List<String>           allLogs;
        private final int                    totalStudents;
        private final int                    totalResults;
        private final int                    successCount;
        private final int                    failCount;

        public MultiAssessmentReport(List<AssessmentReport> individualReports,
                                     List<String> allLogs,
                                     int totalStudents, int totalResults,
                                     int successCount, int failCount) {
            this.individualReports = individualReports;
            this.allLogs           = allLogs;
            this.totalStudents     = totalStudents;
            this.totalResults      = totalResults;
            this.successCount      = successCount;
            this.failCount         = failCount;
        }

        public static MultiAssessmentReport empty(String reason) {
            return new MultiAssessmentReport(
                    Collections.emptyList(),
                    List.of("❌ " + reason),
                    0, 0, 0, 0);
        }

        public boolean overallSuccess()               { return failCount == 0; }
        public List<AssessmentReport> getIndividualReports() { return individualReports; }
        public List<String>           getAllLogs()     { return allLogs; }
        public int                    getTotalStudents() { return totalStudents; }
        public int                    getTotalResults()  { return totalResults; }
        public int                    getSuccessCount()  { return successCount; }
        public int                    getFailCount()     { return failCount; }
    }
}