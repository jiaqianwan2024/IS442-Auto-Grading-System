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
import com.autogradingsystem.testcasegenerator.controller.TestCaseGeneratorController;
import com.autogradingsystem.testcasegenerator.model.QuestionSpec;
import com.autogradingsystem.testcasegenerator.service.TemplateTestSpecBuilder;
import com.autogradingsystem.multiassessment.AssessmentPathConfig;

import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

/**
 * GradingService - Wraps the existing grading pipeline for Spring Boot web
 * layer
 *
 * PACKAGE: com.autogradingsystem.web.service
 * PURPOSE: Bridge between web controller and backend pipeline
 *
 * PIPELINE (7 phases):
 * 1. Validate — check required input files exist (testers NOT required)
 * 2. Extraction — unzip and resolve student identities
 * 3. Test Gen — SKIPPED when examiner-saved testers exist on disk.
 * Only runs when no *Tester.java files are present at all
 * (e.g. first run with no prior /save-testers call).
 * 4. Discovery — build grading plan from template + testers on disk
 * 5. Execution — compile and run each student's code against testers
 * 6. Plagiarism — fingerprint + flag suspicious pairs
 * 7. Analysis — export combined score sheet + statistics XLSX
 *
 * FIX: Phase 3 previously called TestCaseGeneratorController which deleted
 * all existing *Tester.java files before regenerating — wiping any edits
 * the examiner saved via /save-testers. Phase 3 is now fully skipped when
 * any *Tester.java file exists in resources/input/testers/.
 */
@Service
public class GradingService {

    // ── Path-aware fields (null = fall back to global PathConfig) ──
    private Path csvScoresheet;
    private Path inputSubmissions;
    private Path inputTemplate;
    private Path inputTesters;
    private Path inputExam;
    private Path outputExtracted;
    private Path outputReports;

    /** No-arg: used by Spring for the existing single-assessment flow */
    public GradingService() {
    }

    /** Path-aware: used by unified per-assessment flow */
    public GradingService(com.autogradingsystem.multiassessment.AssessmentPathConfig paths) {
        this.csvScoresheet = paths.CSV_SCORESHEET;
        this.inputSubmissions = paths.INPUT_SUBMISSIONS;
        this.inputTemplate = paths.INPUT_TEMPLATE;
        this.inputTesters = paths.INPUT_TESTERS;
        this.inputExam = paths.INPUT_EXAM;
        this.outputExtracted = paths.OUTPUT_EXTRACTED;
        this.outputReports = paths.OUTPUT_REPORTS;
    }

    // ── Resolve helpers ──
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
        List<String> logs = new ArrayList<>();

        try {
            System.out.println("🚀 Pipeline starting...");

            // ── 1. Validate inputs ───────────────────────────────────────────
            System.out.println("🚀 Phase 1: Validating inputs...");
            if (isPathAware()) {
                // Per-assessment: directories already created by upload, just check they exist
                if (!Files.exists(csvScoresheet) || !Files.isDirectory(inputSubmissions)) {
                    return new GradingReport(false, 0, Collections.emptyList(),
                            List.of("❌ Missing required input files for this assessment."));
                }
                outputExtracted.toFile().mkdirs();
                outputReports.toFile().mkdirs();
            } else {
                if (!PathConfig.validateInputPaths()) {
                    return new GradingReport(false, 0, Collections.emptyList(),
                            List.of("❌ Missing required input files. Check resources/input/."));
                }
                PathConfig.ensureOutputDirectories();
            }
            System.out.println("✅ Phase 1 done.");

            // ── 2. Extraction ────────────────────────────────────────────────
            System.out.println("🚀 Phase 2: Extracting submissions...");
            ExtractionController extractionController = isPathAware()
                    ? new ExtractionController(csvScoresheet, inputSubmissions, outputExtracted)
                    : new ExtractionController();
            int studentCount = extractionController.extractAndValidate();
            logs.add("✅ Extracted " + studentCount + " submissions");
            System.out.println("✅ Phase 2 done — " + studentCount + " submissions.");

            // ── 3. Test Case Generation ──────────────────────────────────────
            // Skip entirely when the examiner has already saved testers via
            // /save-testers. Any *Tester.java file present in the testers
            // directory means we must NOT regenerate — that would delete the
            // examiner's edits.
            System.out.println("🚀 Phase 3: Checking for saved testers...");
            if (savedTestersExist()) {
                logs.add("📝 Using examiner-saved testers (AI generation skipped)");
                System.out.println("✅ Phase 3 skipped — examiner-saved testers found on disk.");
            } else {
                // No testers on disk at all — generate from scratch
                Path templateZip = findTemplateZip();
                if (templateZip != null) {
                    System.out.println("   📄 No saved testers found — generating from template...");
                    TemplateTestSpecBuilder specBuilder = new TemplateTestSpecBuilder();
                    Map<String, QuestionSpec> specs = specBuilder.buildQuestionSpecs(templateZip);
                    System.out.println("   📋 Questions found: " + specs.keySet());
                    Map<String, Integer> weights = specBuilder.readScoreWeights();
                    System.out.println("   ⚖️  Weights: " + weights);

                    TestCaseGeneratorController testGenController = new TestCaseGeneratorController();
                    testGenController.generateIfNeeded(specs, weights);
                    logs.add("⚙️  Test cases generated from template ("
                            + specs.size() + " question(s), weights: " + weights + ")");
                    System.out.println("✅ Phase 3 done.");
                } else {
                    logs.add("⚠️  No template ZIP found — skipping test generation");
                    System.out.println("⚠️  Phase 3 skipped — no template ZIP.");
                }
            }

            // ── 4. Discovery ─────────────────────────────────────────────────
            DiscoveryController discoveryController = isPathAware()
                    ? new DiscoveryController(inputTemplate, inputTesters)
                    : new DiscoveryController();
            GradingPlan gradingPlan = discoveryController.buildGradingPlan();
            logs.add("✅ Grading plan: " + gradingPlan.getTaskCount() + " tasks");

            // ── 5. Execution ─────────────────────────────────────────────────
            ExecutionController executionController = isPathAware()
                    ? new ExecutionController(outputExtracted, csvScoresheet, inputTesters, inputTemplate)
                    : new ExecutionController();
            List<GradingResult> results = executionController.gradeAllStudents(gradingPlan);
            Map<String, String> remarks = executionController.getRemarksByStudent();
            Map<String, String> anomalyRmks = executionController.getAnomalyRemarksByStudent();
            List<Student> allStudents = executionController.getLastGradedStudents();
            logs.add("✅ Graded " + results.size() + " results");

            // ── 6. Plagiarism detection ───────────────────────────────────────
            PlagiarismController plagController = isPathAware()
                    ? new PlagiarismController(outputExtracted, outputReports)
                    : new PlagiarismController();
            PlagiarismController.PlagiarismSummary plagSummary = plagController.runPlagiarismCheck(gradingPlan);
            Map<String, String> plagiarismNotes = buildPlagiarismNotes(plagSummary);

            if (plagSummary.hasSuspiciousPairs()) {
                logs.add("🚨 Plagiarism: " + plagSummary.getFlaggedPairCount()
                        + " suspicious pair(s) — see IS442-Plagiarism-Report.xlsx");
            } else {
                logs.add("✅ Plagiarism: no suspicious pairs detected");
            }

            // ── 7. Analysis & export ──────────────────────────────────────────
            AnalysisController analysisController = isPathAware()
                    ? new AnalysisController(csvScoresheet, outputReports, inputTesters)
                    : new AnalysisController();
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

    // ── Private: skip Phase 3 when testers are already on disk ────────────

    private boolean savedTestersExist() {
        Path testersDir = resolveInputTesters();
        if (!Files.isDirectory(testersDir))
            return false;
        try (Stream<Path> files = Files.list(testersDir)) {
            boolean found = files.anyMatch(p -> p.getFileName().toString().endsWith("Tester.java"));
            if (found) {
                System.out.println("   🔒 Testers already on disk — skipping AI generation.");
            }
            return found;
        } catch (Exception e) {
            return false;
        }
    }

    // ── Private: find template ZIP ─────────────────────────────────────────

    private Path findTemplateZip() {
        Path templateDir = resolveInputTemplate();
        try (var stream = Files.list(templateDir)) {
            return stream.filter(p -> p.toString().endsWith(".zip"))
                    .findFirst().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    // ── Private: build per-student plagiarism notes ────────────────────────

    private Map<String, String> buildPlagiarismNotes(
            PlagiarismController.PlagiarismSummary plagSummary) {

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
                parts.add(qEntry.getKey() + ": flagged "
                        + String.join(", ", qEntry.getValue()));
            }
            notes.put(studentEntry.getKey(), String.join("; ", parts));
        }
        return notes;
    }

    // ── Inner class: GradingReport ─────────────────────────────────────────

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

        public void setResults(List<GradingResult> r) {
            this.results = r;
        }

        public List<String> getLogs() {
            return logs;
        }
    }
}