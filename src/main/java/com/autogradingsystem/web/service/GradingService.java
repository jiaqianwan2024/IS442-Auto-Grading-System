package com.autogradingsystem.web.service;

import com.autogradingsystem.PathConfig;
import com.autogradingsystem.extraction.controller.ExtractionController;
import com.autogradingsystem.discovery.controller.DiscoveryController;
import com.autogradingsystem.execution.controller.ExecutionController;
import com.autogradingsystem.analysis.controller.AnalysisController;
import com.autogradingsystem.model.GradingPlan;
import com.autogradingsystem.model.GradingResult;
import com.autogradingsystem.model.Student;
import com.autogradingsystem.plagiarism.controller.PlagiarismController;
import com.autogradingsystem.plagiarism.model.PlagiarismResult;

import org.springframework.stereotype.Service;

import java.util.*;

/**
 * GradingService - Wraps the existing grading pipeline for Spring Boot web layer
 *
 * PACKAGE: com.autogradingsystem.web.service
 * PURPOSE: Bridge between web controller and backend pipeline
 *
 * ARCHITECTURE:
 * GradingController (web request)
 *     → GradingService (this class - WEB LAYER)
 *         → ExtractionController (Phase 1 - BACKEND)
 *         → DiscoveryController  (Phase 2 - BACKEND)
 *         → ExecutionController  (Phase 3 - BACKEND)
 *         → PlagiarismController (Phase 4 - BACKEND)
 *         → AnalysisController   (Results  - BACKEND)
 */
@Service
public class GradingService {

    public GradingReport runFullPipeline() {
        List<String> logs = new ArrayList<>();

        try {
            // ── 1. Validate inputs ───────────────────────────────────────────
            if (!PathConfig.validateInputPaths()) {
                return new GradingReport(false, 0, Collections.emptyList(),
                        List.of("❌ Missing required input files. Check resources/input/."));
            }

            PathConfig.ensureOutputDirectories();

            // ── 2. Extraction ────────────────────────────────────────────────
            ExtractionController extractionController = new ExtractionController();
            int studentCount = extractionController.extractAndValidate();
            logs.add("✅ Extracted " + studentCount + " submissions");

            // ── 3. Discovery ─────────────────────────────────────────────────
            DiscoveryController discoveryController = new DiscoveryController();
            GradingPlan gradingPlan = discoveryController.buildGradingPlan();
            logs.add("✅ Grading plan: " + gradingPlan.getTaskCount() + " tasks");

            // ── 4. Execution ─────────────────────────────────────────────────
            ExecutionController executionController = new ExecutionController();
            List<GradingResult> results = executionController.gradeAllStudents(gradingPlan);
            Map<String, String> remarks     = executionController.getRemarksByStudent();
            Map<String, String> anomalyRmks = executionController.getAnomalyRemarksByStudent();
            List<Student>       allStudents = executionController.getLastGradedStudents();
            logs.add("✅ Graded " + results.size() + " results");

            // ── 5. Plagiarism detection ───────────────────────────────────────
            PlagiarismController plagController = new PlagiarismController();
            PlagiarismController.PlagiarismSummary plagSummary =
                    plagController.runPlagiarismCheck(gradingPlan);

            // Build the plagiarismNotes map:
            //   studentId → "Q1a: flagged with ping.lee.2023 (87.3%); Q2b: flagged with tan.jun.2024 (91.0%)"
            // This is what appears in the new "Plagiarism" column of the score sheet.
            Map<String, String> plagiarismNotes = buildPlagiarismNotes(plagSummary);

            if (plagSummary.hasSuspiciousPairs()) {
                logs.add("🚨 Plagiarism: " + plagSummary.getFlaggedPairCount()
                        + " suspicious pair(s) — see IS442-Plagiarism-Report.xlsx");
            } else {
                logs.add("✅ Plagiarism: no suspicious pairs detected");
            }

            // ── 6. Analysis & export ──────────────────────────────────────────
            AnalysisController analysisController = new AnalysisController();
            analysisController.analyzeAndDisplay(results, remarks, anomalyRmks, allStudents,
                                                 plagiarismNotes);
            logs.add("✅ Reports exported");

            return new GradingReport(true, studentCount, results, logs);

        } catch (Exception e) {
            logs.add("❌ Pipeline failed: " + e.getMessage());
            e.printStackTrace();
            return new GradingReport(false, 0, Collections.emptyList(), logs);
        }
    }

    // ── Private: build per-student plagiarism notes ───────────────────────

    /**
     * Converts the PlagiarismSummary into a map of student → readable note.
     *
     * For each flagged student, the note lists every question where they were
     * flagged and names the other student(s) involved, e.g.:
     *   "Q1a: flagged with ping.lee.2023 (87.3%); Q2b: flagged with tan.jun.2024 (91.0%)"
     *
     * Students who were not flagged are absent from the map (empty cell in Excel).
     */
    private Map<String, String> buildPlagiarismNotes(
            PlagiarismController.PlagiarismSummary plagSummary) {

        // Accumulate per student: questionId → list of "with <other> (xx.x%)"
        Map<String, Map<String, List<String>>> perStudentPerQuestion = new LinkedHashMap<>();

        for (PlagiarismResult r : plagSummary.flaggedResults) {
            String a   = r.getStudentA();
            String b   = r.getStudentB();
            String qid = r.getQuestionId();
            String pct = String.format("%.1f%%", r.getSimilarityPercent());

            // Note for student A: names student B
            perStudentPerQuestion
                .computeIfAbsent(a, k -> new LinkedHashMap<>())
                .computeIfAbsent(qid, k -> new ArrayList<>())
                .add("with " + b + " (" + pct + ")");

            // Note for student B: names student A
            perStudentPerQuestion
                .computeIfAbsent(b, k -> new LinkedHashMap<>())
                .computeIfAbsent(qid, k -> new ArrayList<>())
                .add("with " + a + " (" + pct + ")");
        }

        // Flatten into a single string per student
        Map<String, String> notes = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, List<String>>> studentEntry
                : perStudentPerQuestion.entrySet()) {

            List<String> parts = new ArrayList<>();
            for (Map.Entry<String, List<String>> qEntry
                    : studentEntry.getValue().entrySet()) {
                parts.add(qEntry.getKey() + ": flagged "
                        + String.join(", ", qEntry.getValue()));
            }
            notes.put(studentEntry.getKey(), String.join("; ", parts));
        }

        return notes;
    }

    // ── Inner class: GradingReport ─────────────────────────────────────────

    public static class GradingReport {
        private boolean             success;
        private int                 studentCount;
        private List<GradingResult> results = new ArrayList<>();
        private List<String>        logs    = new ArrayList<>();

        public GradingReport() {}

        public GradingReport(boolean success, int studentCount,
                             List<GradingResult> results, List<String> logs) {
            this.success      = success;
            this.studentCount = studentCount;
            this.results      = results != null ? results : new ArrayList<>();
            this.logs         = logs    != null ? logs    : new ArrayList<>();
        }

        public void addLog(String message)  { this.logs.add(message); }

        public boolean             isSuccess()                          { return success; }
        public void                setSuccess(boolean success)          { this.success = success; }
        public int                 getStudentCount()                    { return studentCount; }
        public void                setStudentCount(int studentCount)    { this.studentCount = studentCount; }
        public List<GradingResult> getResults()                        { return results; }
        public void                setResults(List<GradingResult> r)   { this.results = r; }
        public List<String>        getLogs()                           { return logs; }
    }
}
