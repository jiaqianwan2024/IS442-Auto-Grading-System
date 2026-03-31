package com.autogradingsystem.web.service;

import com.autogradingsystem.PathConfig;
import com.autogradingsystem.analysis.controller.AnalysisController;
import com.autogradingsystem.discovery.controller.DiscoveryController;
import com.autogradingsystem.execution.controller.ExecutionController;
import com.autogradingsystem.extraction.controller.ExtractionController;
import com.autogradingsystem.model.GradingPlan;
import com.autogradingsystem.model.GradingResult;
import com.autogradingsystem.model.Student;
import com.autogradingsystem.multiassessment.AssessmentPathConfig;
import com.autogradingsystem.plagiarism.controller.PlagiarismController;
import com.autogradingsystem.plagiarism.model.PlagiarismResult;
import com.autogradingsystem.testcasegenerator.controller.TestCaseGeneratorController;
import com.autogradingsystem.testcasegenerator.model.QuestionSpec;
import com.autogradingsystem.testcasegenerator.service.TemplateTestSpecBuilder;

import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Service
public class GradingService {

    private Path csvScoresheet;
    private Path inputSubmissions;
    private Path inputTemplate;
    private Path inputTesters;
    private Path inputExam;
    private Path outputExtracted;
    private Path outputReports;

    public GradingService() {
    }

    public GradingService(AssessmentPathConfig paths) {
        this.csvScoresheet = paths.CSV_SCORESHEET;
        this.inputSubmissions = paths.INPUT_SUBMISSIONS;
        this.inputTemplate = paths.INPUT_TEMPLATE;
        this.inputTesters = paths.INPUT_TESTERS;
        this.inputExam = paths.INPUT_EXAM;
        this.outputExtracted = paths.OUTPUT_EXTRACTED;
        this.outputReports = paths.OUTPUT_REPORTS;
    }

    private Path resolveInputTesters() {
        return inputTesters != null ? inputTesters : PathConfig.INPUT_TESTERS;
    }

    private Path resolveInputTemplate() {
        return inputTemplate != null ? inputTemplate : Paths.get("resources/input/template");
    }

    private boolean isPathAware() {
        return csvScoresheet != null;
    }

    public GradingReport runFullPipeline() {
        return runFullPipeline(null);
    }

    public GradingReport runFullPipeline(String assessmentName) {
        List<String> logs = new ArrayList<>();

        try {
            progress(assessmentName, 2, "Starting", "Preparing grading pipeline...");

            progress(assessmentName, 8, "Validating", "Checking required files...");
            if (isPathAware()) {
                if (!Files.exists(csvScoresheet) || !Files.isDirectory(inputSubmissions)) {
                    progress(assessmentName, 100, "Failed", "Missing required assessment input files.");
                    return new GradingReport(false, 0, Collections.emptyList(),
                            List.of("Missing required input files for this assessment."));
                }
                outputExtracted.toFile().mkdirs();
                outputReports.toFile().mkdirs();
            } else {
                if (!PathConfig.validateInputPaths()) {
                    progress(assessmentName, 100, "Failed", "Missing required input files.");
                    return new GradingReport(false, 0, Collections.emptyList(),
                            List.of("Missing required input files. Check resources/input/."));
                }
                PathConfig.ensureOutputDirectories();
            }
            progress(assessmentName, 12, "Validated", "Required files are ready.");

            progress(assessmentName, 18, "Extracting", "Unzipping submissions and resolving identities...");
            ExtractionController extractionController = isPathAware()
                    ? new ExtractionController(csvScoresheet, inputSubmissions, outputExtracted)
                    : new ExtractionController();
            int studentCount = extractionController.extractAndValidate();
            logs.add("Extracted " + studentCount + " submissions");
            progress(assessmentName, 28, "Extracted", "Extracted " + studentCount + " submission(s).");

            progress(assessmentName, 34, "Preparing Testers", "Checking saved testers and generation requirements...");
            if (savedTestersExist()) {
                logs.add("Using examiner-saved testers (AI generation skipped)");
                progress(assessmentName, 40, "Using Saved Testers", "Using examiner-provided testers.");
            } else {
                Path templateZip = findTemplateZip();
                if (templateZip != null) {
                    TemplateTestSpecBuilder specBuilder = new TemplateTestSpecBuilder();
                    Map<String, QuestionSpec> specs = specBuilder.buildQuestionSpecs(templateZip);
                    Map<String, Integer> weights = specBuilder.readScoreWeights();

                    TestCaseGeneratorController testGenController = isPathAware()
                            ? new TestCaseGeneratorController(inputTesters, inputExam)
                            : new TestCaseGeneratorController();
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
            DiscoveryController discoveryController = isPathAware()
                    ? new DiscoveryController(inputTemplate, inputTesters)
                    : new DiscoveryController();
            GradingPlan gradingPlan = discoveryController.buildGradingPlan();
            logs.add("Grading plan: " + gradingPlan.getTaskCount() + " tasks");
            progress(assessmentName, 55, "Discovered", "Found " + gradingPlan.getTaskCount() + " grading task(s).");

            progress(assessmentName, 55, "Grading", "Running testers against student submissions...");
            ExecutionController executionController = isPathAware()
                    ? new ExecutionController(outputExtracted, csvScoresheet, inputTesters, inputTemplate)
                    : new ExecutionController();
            List<GradingResult> results = executionController.gradeAllStudents(
                    gradingPlan,
                    (completed, total) -> progressExecution(assessmentName, completed, total));
            Map<String, String> remarks = executionController.getRemarksByStudent();
            Map<String, String> anomalyRemarks = executionController.getAnomalyRemarksByStudent();
            List<Student> allStudents = executionController.getLastGradedStudents();
            logs.add("Graded " + results.size() + " results");
            progress(assessmentName, 86, "Graded", "Completed execution for " + results.size() + " result(s).");

            progress(assessmentName, 90, "Analyzing Plagiarism", "Checking submissions for similarity...");
            PlagiarismController plagController = isPathAware()
                    ? new PlagiarismController(outputExtracted, outputReports)
                    : new PlagiarismController();
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
            AnalysisController analysisController = isPathAware()
                    ? new AnalysisController(csvScoresheet, outputReports, inputTesters)
                    : new AnalysisController();
            analysisController.analyzeAndDisplay(results, remarks, anomalyRemarks, allStudents, plagiarismNotes);
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
        Path testersDir = resolveInputTesters();
        if (!Files.isDirectory(testersDir)) {
            return false;
        }
        try (Stream<Path> files = Files.list(testersDir)) {
            return files.anyMatch(p -> p.getFileName().toString().endsWith("Tester.java"));
        } catch (Exception e) {
            return false;
        }
    }

    private Path findTemplateZip() {
        Path templateDir = resolveInputTemplate();
        try (var stream = Files.list(templateDir)) {
            return stream.filter(p -> p.toString().endsWith(".zip")).findFirst().orElse(null);
        } catch (Exception e) {
            return null;
        }
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
