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
import com.autogradingsystem.penalty.model.PenaltyGradingResult;
import com.autogradingsystem.penalty.model.ProcessedScore;
import com.autogradingsystem.plagiarism.controller.PlagiarismController;
import com.autogradingsystem.plagiarism.model.PlagiarismResult;
import com.autogradingsystem.analysis.service.ScoreAnalyzer;
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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class GradingService {
    private static final Pattern QUESTION_FILE_PATTERN = Pattern.compile("\\b(Q\\d+[A-Za-z0-9]*)\\.java\\b");


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

    /**
     * Runs the full 7-phase grading pipeline with optional penalty application.
     *
     * @param assessmentName Sanitised assessment key for progress tracking
     * @param applyPenalties If true, runs penalty phase between execution and analysis.
     *                       Penalty data (per-student deductions + adjusted totals) is
     *                       passed to AnalysisController so the score sheet includes
     *                       "Penalty Deduction" and "Adjusted Score" columns.
     */
    public GradingReport runFullPipeline(String assessmentName, boolean applyPenalties) {
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

            progress(assessmentName, 34, "Preparing Testers", "Checking saved testers and generation requirements...");
            if (savedTestersExist()) {
                logs.add("Using examiner-saved testers (AI generation skipped)");
                progress(assessmentName, 40, "Using Saved Testers", "Using examiner-provided testers.");
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
            logs.add("Grading plan: " + gradingPlan.getTaskCount() + " tasks");
            progress(assessmentName, 55, "Discovered", "Found " + gradingPlan.getTaskCount() + " grading task(s).");

            progress(assessmentName, 55, "Grading", "Running testers against student submissions...");
            ExecutionController executionController =
                    new ExecutionController(outputExtracted, csvScoresheet, inputTesters, inputTemplate);
            List<GradingResult> results = executionController.gradeAllStudents(
                    gradingPlan,
                    (completed, total) -> progressExecution(assessmentName, completed, total));
            Map<String, String> remarks = executionController.getRemarksByStudent();
            Map<String, String> anomalyRemarks = executionController.getAnomalyRemarksByStudent();
            List<Student> allStudents = executionController.getLastGradedStudents();
            logs.add("Graded " + results.size() + " results");
            progress(assessmentName, 86, "Graded", "Completed execution for " + results.size() + " result(s).");

            // ══════════════════════════════════════════════════════════════
            // NEW PHASE: PENALTY APPLICATION (between execution and plagiarism)
            // ══════════════════════════════════════════════════════════════
            Map<String, ProcessedScore> penaltyResults = Collections.emptyMap();

            if (applyPenalties) {
                progress(assessmentName, 88, "Applying Penalties", "Calculating penalty deductions...");
                penaltyResults = runPenaltyPhase(results, remarks, allStudents);
                logs.add("Penalties applied to " + countStudentsWithPenalty(penaltyResults) + " student(s)");
                progress(assessmentName, 89, "Penalties Applied", "Penalty deductions calculated.");
            } else {
                logs.add("Penalties skipped (not enabled for this run)");
            }

            progress(assessmentName, 90, "Analyzing Plagiarism", "Checking submissions for similarity...");
            PlagiarismController plagController =
                    new PlagiarismController(outputExtracted, outputReports,
                                            AssessmentPathConfig.toDisplayTitle(assessmentName));
            PlagiarismController.PlagiarismSummary plagSummary = plagController.runPlagiarismCheck(gradingPlan);
            Map<String, String> plagiarismNotes = buildPlagiarismNotes(plagSummary);

            String displayTitle = AssessmentPathConfig.toDisplayTitle(assessmentName);
            if (plagSummary.hasSuspiciousPairs()) {
                logs.add("Plagiarism: " + plagSummary.getFlaggedPairCount()
                        + " suspicious pair(s) - see " + displayTitle + "-Plagiarism-Report.xlsx");
            } else {
                logs.add("Plagiarism: no suspicious pairs detected");
            }
            progress(assessmentName, 94, "Plagiarism Complete", "Plagiarism analysis finished.");

            progress(assessmentName, 96, "Exporting Reports", "Generating score sheet and reports...");
            AnalysisController analysisController =
                    new AnalysisController(csvScoresheet, outputReports, inputTesters,
                                          AssessmentPathConfig.toDisplayTitle(assessmentName));
            analysisController.analyzeAndDisplayWithPenalties(results, remarks, anomalyRemarks,
                    allStudents, plagiarismNotes, penaltyResults);
            logs.add("Reports exported" + (applyPenalties ? "" : ""));
            progress(assessmentName, 100, "Completed", "Reports exported.");

            return new GradingReport(true, studentCount, results, logs);

        } catch (Exception e) {
            logs.add("Pipeline failed: " + e.getMessage());
            progress(assessmentName, 100, "Failed", "Grading failed: " + e.getMessage());
            e.printStackTrace();
            return new GradingReport(false, 0, Collections.emptyList(), logs);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PENALTY PHASE — builds PenaltyGradingResult per student, runs through
    // PenaltyController, returns per-student ProcessedScore map.
    // ══════════════════════════════════════════════════════════════════════════

    private Map<String, ProcessedScore> runPenaltyPhase(
            List<GradingResult> results,
            Map<String, String> remarks,
            List<Student> allStudents) {

        Map<String, ProcessedScore> penaltyMap = new LinkedHashMap<>();
        PenaltyController penaltyController = new PenaltyController();

        // Group results by student
        Map<String, List<GradingResult>> byStudent = ScoreAnalyzer.groupByStudent(results);

        // Build a quick lookup: studentId → Student object
        Map<String, Student> studentLookup = new LinkedHashMap<>();
        for (Student s : allStudents) {
            studentLookup.put(s.getId(), s);
        }

        for (Map.Entry<String, List<GradingResult>> entry : byStudent.entrySet()) {
            String studentId = entry.getKey();
            List<GradingResult> studentResults = entry.getValue();
            Student student = studentLookup.get(studentId);
            String studentRemarks = remarks.getOrDefault(studentId, "");

            boolean rootFolderCorrect = student == null || student.isFolderRenamed();
            Set<String> headerPenaltyQuestions = extractHeaderPenaltyQuestionIds(studentRemarks);
            Set<String> wrongPackageQuestions = extractQuestionIds(studentRemarks, ":WrongPackage:");
            Set<String> hierarchyQuestions = extractQuestionIds(studentRemarks, ":ImproperHierarchy");

            List<PenaltyGradingResult> penaltyInputs = new ArrayList<>();

            for (GradingResult gr : studentResults) {
                double rawScore = gr.getScore();
                double maxScore = ScoreAnalyzer.getMaxScoreFromTester(
                        gr.getQuestionId(), inputTesters);
                String questionId = gr.getQuestionId();
                String parentQuestionId = parentQuestionId(questionId);

                boolean properHierarchy = !hierarchyQuestions.contains(questionId)
                        && !hierarchyQuestions.contains(parentQuestionId);
                boolean hasHeaders = !headerPenaltyQuestions.contains(questionId);
                boolean hasWrongPackage = wrongPackageQuestions.contains(questionId);

                penaltyInputs.add(new PenaltyGradingResult(
                        questionId,
                        rawScore,
                        maxScore,
                        rootFolderCorrect,
                        properHierarchy,
                        hasHeaders,
                        hasWrongPackage));
            }

            try {
                ProcessedScore processed = penaltyController.processStudentResults(studentId, penaltyInputs);
                penaltyMap.put(studentId, processed);
            } catch (Exception e) {
                System.err.println("⚠️  Penalty calculation failed for "
                        + studentId + ": " + e.getMessage());
                // Fallback: no deduction
                double total = studentResults.stream()
                        .mapToDouble(GradingResult::getScore).sum();
                penaltyMap.put(studentId, new ProcessedScore(total, 0.0, total, "None"));
            }
        }

        // Console summary
        System.out.println("\n" + "=".repeat(70));
        System.out.println("💰 PENALTY SUMMARY");
        System.out.println("=".repeat(70));
        for (Map.Entry<String, ProcessedScore> e : penaltyMap.entrySet()) {
            ProcessedScore ps = e.getValue();
            if (ps.getTotalDeduction() > 0) {
                System.out.printf("   %-25s Raw: %.1f  Deduction: -%.1f  Adjusted: %.1f%n",
                        e.getKey(), ps.getRawScore(), ps.getTotalDeduction(), ps.getFinalScore());
            }
        }
        long penalised = countStudentsWithPenalty(penaltyMap);
        System.out.println("   " + penalised + " student(s) received deductions.");
        System.out.println("=".repeat(70));

        return penaltyMap;
    }

    private long countStudentsWithPenalty(Map<String, ProcessedScore> penaltyMap) {
        if (penaltyMap == null || penaltyMap.isEmpty()) {
            return 0;
        }
        return penaltyMap.values().stream()
                .filter(p -> p != null && p.getFinalScore() < p.getRawScore() - 1e-9)
                .count();
    }

    private Set<String> extractQuestionIds(String remarks, String marker) {
        Set<String> ids = new java.util.LinkedHashSet<>();
        if (remarks == null || remarks.isBlank()) {
            return ids;
        }

        String[] tokens = remarks.split("\\s*;\\s*");
        for (String token : tokens) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            if ("NoHeader:".equals(marker) && trimmed.startsWith("NoHeader:")) {
                String fileName = trimmed.substring("NoHeader:".length()).trim();
                if (fileName.endsWith(".java")) {
                    ids.add(fileName.substring(0, fileName.length() - 5));
                } else if (!fileName.isBlank()) {
                    ids.add(fileName);
                }
                continue;
            }

            if (trimmed.contains(marker)) {
                int colon = trimmed.indexOf(':');
                if (colon > 0) {
                    ids.add(trimmed.substring(0, colon).trim());
                }
            }
        }
        return ids;
    }

    private String parentQuestionId(String questionId) {
        if (questionId == null || questionId.isBlank()) {
            return "Question";
        }
        if (questionId.length() >= 3) {
            char last = questionId.charAt(questionId.length() - 1);
            char secondLast = questionId.charAt(questionId.length() - 2);
            if (Character.isLetter(last) && Character.isDigit(secondLast)) {
                return questionId.substring(0, questionId.length() - 1);
            }
        }
        return questionId;
    }

    private Set<String> extractHeaderPenaltyQuestionIds(String remarks) {
        Set<String> ids = extractQuestionIds(remarks, "NoHeader:");
        if (remarks == null || remarks.isBlank()) {
            return ids;
        }

        String[] tokens = remarks.split("\\s*;\\s*");
        for (String token : tokens) {
            String trimmed = token.trim();
            if (!trimmed.startsWith("HeaderMismatch:")) {
                continue;
            }

            Matcher matcher = QUESTION_FILE_PATTERN.matcher(trimmed);
            if (matcher.find()) {
                ids.add(matcher.group(1));
            }
        }
        return ids;
    }

    // ── Existing helpers (unchanged) ──────────────────────────────────────────

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
