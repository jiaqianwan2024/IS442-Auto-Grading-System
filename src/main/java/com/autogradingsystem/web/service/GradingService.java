package com.autogradingsystem.web.service;

import com.autogradingsystem.analysis.controller.AnalysisController;
import com.autogradingsystem.discovery.controller.DiscoveryController;
import com.autogradingsystem.execution.controller.ExecutionController;
import com.autogradingsystem.extraction.controller.ExtractionController;
import com.autogradingsystem.model.GradingPlan;
import com.autogradingsystem.model.GradingResult;
import com.autogradingsystem.model.Student;
import com.autogradingsystem.multiassessment.AssessmentPathConfig;
import com.autogradingsystem.penalty.controller.PenaltyController;
import com.autogradingsystem.penalty.service.PenaltyRemarksParser;
import com.autogradingsystem.plagiarism.controller.PlagiarismController;
import com.autogradingsystem.plagiarism.model.PlagiarismResult;
import com.autogradingsystem.testcasegenerator.controller.TestCaseGeneratorController;
import com.autogradingsystem.testcasegenerator.model.QuestionSpec;
import com.autogradingsystem.testcasegenerator.service.TemplateTestSpecBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class GradingService {

    private Path csvScoresheet;
    private Path inputSubmissions;
    private Path inputTemplate;
    private Path inputTesters;
    private Path inputExam;
    private Path outputExtracted;
    private Path outputReports;

    public GradingService(AssessmentPathConfig paths) {
        this.csvScoresheet = paths.CSV_SCORESHEET;
        this.inputSubmissions = paths.INPUT_SUBMISSIONS;
        this.inputTemplate = paths.INPUT_TEMPLATE;
        this.inputTesters = paths.INPUT_TESTERS;
        this.inputExam = paths.INPUT_EXAM;
        this.outputExtracted = paths.OUTPUT_EXTRACTED;
        this.outputReports = paths.OUTPUT_REPORTS;
    }

    public GradingReport runFullPipeline(String assessmentName) {
        List<String> logs = new ArrayList<>();

        try {
            progress(assessmentName, 2, "Starting", "Preparing grading pipeline...");

            progress(assessmentName, 8, "Validating", "Checking required files...");
            if (!Files.exists(csvScoresheet) || !Files.isDirectory(inputSubmissions)) {
                progress(assessmentName, 100, "Failed", "Missing required assessment input files.");
                return new GradingReport(false, 0, Collections.emptyList(),
                        List.of("Missing required input files for this assessment."));
            }
            outputExtracted.toFile().mkdirs();
            outputReports.toFile().mkdirs();
            progress(assessmentName, 12, "Validated", "Required files are ready.");

            progress(assessmentName, 18, "Extracting", "Unzipping submissions and resolving identities...");
            ExtractionController extractionController =
                    new ExtractionController(csvScoresheet, inputSubmissions, outputExtracted);
            int studentCount = extractionController.extractAndValidate();
            logs.add("Extracted " + studentCount + " submissions");
            progress(assessmentName, 28, "Extracted", "Extracted " + studentCount + " submission(s).");

            progress(assessmentName, 34, "Preparing Testers", "Checking testers folder and generation requirements...");
            if (savedTestersExist()) {
                progress(assessmentName, 40, "Testers Ready", "Testers loaded from input/testers/.");
            } else {
                Path templateZip = findTemplateZip();
                if (templateZip != null) {
                    TemplateTestSpecBuilder specBuilder = new TemplateTestSpecBuilder(inputExam);
                    Map<String, QuestionSpec> specs = specBuilder.buildQuestionSpecs(templateZip);
                    Map<String, Integer> weights = specBuilder.readScoreWeights();

                    TestCaseGeneratorController testGenController =
                            new TestCaseGeneratorController(inputTesters, inputExam, inputTemplate);
                    testGenController.generateIfNeeded(specs, weights);
                    logs.add("Test cases generated from template (" + specs.size()
                            + " question(s), weights: " + weights + ")");
                    progress(assessmentName, 40, "Generated Testers",
                            "Generated testers for " + specs.size() + " question(s).");
                } else {
                    logs.add("No template ZIP found - skipping test generation");
                    progress(assessmentName, 40, "Skipped Test Generation", "No template ZIP found.");
                }
            }

            progress(assessmentName, 45, "Discovering", "Building grading plan...");
            DiscoveryController discoveryController =
                    new DiscoveryController(inputTemplate, inputTesters);
            GradingPlan gradingPlan = discoveryController.buildGradingPlan();
            int parts = gradingPlan.getTaskCount();
            logs.add("Grading plan: " + parts + " question part" + (parts == 1 ? "" : "s"));
            progress(assessmentName, 55, "Discovered", "Found " + parts + " question part(s).");

            progress(assessmentName, 55, "Grading", "Running testers against student submissions...");
            ExecutionController executionController =
                    new ExecutionController(outputExtracted, csvScoresheet, inputTesters, inputTemplate);
            List<GradingResult> results = executionController.gradeAllStudents(
                    gradingPlan,
                    (completed, total) -> progressExecution(assessmentName, completed, total));
            Map<String, String> remarks = executionController.getRemarksByStudent();
            Map<String, String> anomalyRemarks = executionController.getAnomalyRemarksByStudent();
            List<Student> allStudents = executionController.getLastGradedStudents();
            int nMarks = results.size();
            logs.add("Graded " + studentCount + " submission" + (studentCount == 1 ? "" : "s"));
            progress(assessmentName, 86, "Graded",
                    "Completed " + nMarks + " question-part mark(s) for " + studentCount + " submission(s).");

            // ── Phase 4.5: Penalties ──────────────────────────────────────
            progress(assessmentName, 87, "Applying Penalties", "Computing penalty deductions...");
            Map<String, String> penaltyRemarks = new LinkedHashMap<>();
            Map<String, Map<String, Double>> penaltyQScores = new LinkedHashMap<>();
            Map<String, Double> penaltyTotals = applyPenalties(results, remarks, penaltyRemarks, penaltyQScores);
            if (!penaltyTotals.isEmpty()) {
                logs.add("Penalties applied for " + penaltyTotals.size() + " student(s)");
            }
            progress(assessmentName, 89, "Penalties Applied", "Penalty deductions computed.");

            progress(assessmentName, 90, "Analyzing Plagiarism", "Checking submissions for similarity...");
            PlagiarismController plagController =
                    new PlagiarismController(outputExtracted, outputReports);
            PlagiarismController.PlagiarismSummary plagSummary = plagController.runPlagiarismCheck(gradingPlan);
            Map<String, String> plagiarismNotes = buildPlagiarismNotes(plagSummary);

            if (plagSummary.hasSuspiciousPairs()) {
                logs.add("Plagiarism: " + plagSummary.getFlaggedPairCount()
                        + " suspicious pair(s) - see IS442-Plagiarism-Report.xlsx");
            } else {
                logs.add("Plagiarism: no suspicious pairs detected");
            }
            progress(assessmentName, 94, "Plagiarism Complete", "Plagiarism analysis finished.");

            progress(assessmentName, 96, "Exporting Reports", "Generating score sheet and reports...");
            AnalysisController analysisController =
                    new AnalysisController(csvScoresheet, outputReports, inputTesters);
            analysisController.analyzeAndDisplay(results, remarks, anomalyRemarks, allStudents, plagiarismNotes, penaltyTotals, penaltyRemarks, penaltyQScores);
            logs.add("Reports exported");
            progress(assessmentName, 100, "Completed", "Reports exported.");

            return new GradingReport(true, studentCount, results, logs);

        } catch (Exception e) {
            logs.add("Pipeline failed: " + e.getMessage());
            progress(assessmentName, 100, "Failed", "Grading failed: " + e.getMessage());
            e.printStackTrace();
            return new GradingReport(false, 0, Collections.emptyList(), logs);
        }
    }

    private void progress(String assessmentName, int percent, String stage, String message) {
        if (assessmentName == null || assessmentName.isBlank()) {
            return;
        }
        AssessmentProgressRegistry.updatePercent(assessmentName, percent, stage, message);
    }

    private void progressExecution(String assessmentName, int completed, int total) {
        if (assessmentName == null || assessmentName.isBlank()) {
            return;
        }
        AssessmentProgressRegistry.updateProgress(
                assessmentName,
                "Grading",
                55,
                completed,
                total,
                "Processed " + completed + " of " + total + " grading task(s).");
    }

    private boolean savedTestersExist() {
        if (!Files.isDirectory(inputTesters)) {
            return false;
        }
        try (Stream<Path> files = Files.list(inputTesters)) {
            return files.anyMatch(p -> p.getFileName().toString().endsWith("Tester.java"));
        } catch (Exception e) {
            return false;
        }
    }

    private Path findTemplateZip() {
        try (var stream = Files.list(inputTemplate)) {
            return stream.filter(p -> p.toString().endsWith(".zip")).findFirst().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Phase 4.5 — delegates to the penalty microservice ({@link PenaltyController}).
     *
     * {@link PenaltyRemarksParser} parses the execution-phase remark string
     * and applies rules 1, 3, and 4.  Rule 2 is reserved until extraction-phase
     * folder-placement data is propagated into grading remarks.
     *
     * @param results      execution grading results
     * @param gradeRemarks remarks produced by ExecutionController per student
     * @param remarksDest  receives human-readable penalty description per student
     * @return studentId → penalty-adjusted total score
     */
    private Map<String, Double> applyPenalties(List<GradingResult> results,
                                               Map<String, String> gradeRemarks,
                                               Map<String, String> remarksDest,
                                               Map<String, Map<String, Double>> penaltyQScoresDest) {
        PenaltyController penaltyController = new PenaltyController();

        Map<String, List<GradingResult>> byStudent = new LinkedHashMap<>();
        for (GradingResult r : results) {
            byStudent.computeIfAbsent(r.getStudent().getUsername(), k -> new ArrayList<>()).add(r);
        }

        Map<String, Double> penaltyTotals = new LinkedHashMap<>();
        for (Map.Entry<String, List<GradingResult>> entry : byStudent.entrySet()) {
            String studentId = entry.getKey();
            Map<String, Double> qScores    = new LinkedHashMap<>();
            Map<String, Double> qMaxScores = new LinkedHashMap<>();
            double rawTotal        = 0.0;
            double totalDenominator = 0.0;
            for (GradingResult r : entry.getValue()) {
                String qId = r.getTask().getQuestionId();
                qScores.put(qId, r.getScore());
                qMaxScores.put(qId, r.getMaxScore());
                rawTotal         += r.getScore();
                totalDenominator += r.getMaxScore();
            }
            String remark = gradeRemarks.getOrDefault(studentId, "");
            PenaltyRemarksParser.PenaltyResult pr =
                    penaltyController.processWithRemarks(studentId, qScores, qMaxScores,
                                                         totalDenominator, rawTotal, remark);
            penaltyTotals.put(studentId, pr.adjustedTotal);
            remarksDest.put(studentId, pr.remarks);
            penaltyQScoresDest.put(studentId, pr.adjustedQScores);
        }
        return penaltyTotals;
    }

    private Map<String, String> buildPlagiarismNotes(PlagiarismController.PlagiarismSummary plagSummary) {
        Map<String, Map<String, List<String>>> perStudentPerQuestion = new LinkedHashMap<>();

        for (PlagiarismResult r : plagSummary.flaggedResults) {
            String a = r.getStudentA();
            String b = r.getStudentB();
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
        for (Map.Entry<String, Map<String, List<String>>> studentEntry : perStudentPerQuestion.entrySet()) {
            List<String> parts = new ArrayList<>();
            for (Map.Entry<String, List<String>> qEntry : studentEntry.getValue().entrySet()) {
                parts.add(qEntry.getKey() + ": flagged " + String.join(", ", qEntry.getValue()));
            }
            notes.put(studentEntry.getKey(), String.join("; ", parts));
        }
        return notes;
    }

    public static class GradingReport {
        private boolean success;
        private int studentCount;
        private List<GradingResult> results = new ArrayList<>();
        private List<String> logs = new ArrayList<>();

        public GradingReport() {
        }

        public GradingReport(boolean success, int studentCount,
                             List<GradingResult> results, List<String> logs) {
            this.success = success;
            this.studentCount = studentCount;
            this.results = results != null ? results : new ArrayList<>();
            this.logs = logs != null ? logs : new ArrayList<>();
        }

        public void addLog(String message) {
            this.logs.add(message);
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public int getStudentCount() {
            return studentCount;
        }

        public void setStudentCount(int studentCount) {
            this.studentCount = studentCount;
        }

        public List<GradingResult> getResults() {
            return results;
        }

        public void setResults(List<GradingResult> results) {
            this.results = results;
        }

        public List<String> getLogs() {
            return logs;
        }
    }
}
