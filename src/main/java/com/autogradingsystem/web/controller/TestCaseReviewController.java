package com.autogradingsystem.web.controller;

import com.autogradingsystem.testcasegenerator.model.QuestionSpec;
import com.autogradingsystem.testcasegenerator.service.ExamPaperParser;
import com.autogradingsystem.testcasegenerator.service.LLMTestOracle;
import com.autogradingsystem.testcasegenerator.service.TemplateTestSpecBuilder;
import com.autogradingsystem.testcasegenerator.service.TesterGenerator;
import com.autogradingsystem.PathConfig;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.*;
import java.util.*;

/**
 * TestCaseReviewController - Handles the full examiner review workflow.
 *
 * PACKAGE: com.autogradingsystem.web.controller
 *
 * WORKFLOW (4 steps):
 *
 *   Step 1 — Upload (submission ZIP, template ZIP, scoresheet CSV)
 *             Exam paper PDF is optional — placed in resources/input/exam/
 *
 *   Step 2 — POST /parse-exam-marks
 *             LLM reads exam PDF (if present) and returns detected Q→marks.
 *             If no PDF, returns empty map so frontend shows manual entry.
 *             Frontend shows a confirmation table — examiner can edit marks.
 *
 *   Step 3 — POST /generate-testers   (body: { marks: { Q1a:3, Q1b:2, ... } })
 *             Uses confirmed marks to generate *Tester.java source via LLM.
 *             Returns { questions: { Q1a: { source: "...", filename: "Q1aTester.java" } } }
 *             Does NOT write files yet — returns raw source for review.
 *
 *   Step 4 — POST /save-testers       (body: { testers: { Q1a: "...java source..." } })
 *             Writes each source string to resources/input/testers/Q1aTester.java.
 *             Frontend can call this any time the examiner saves edits.
 *             After this, POST /grade runs normally.
 */
@RestController
public class TestCaseReviewController {

    // ── Path-aware fields (null = fall back to global PathConfig) ──
    private Path inputTesters;
    private Path inputTemplate;
    private Path inputExam;

    /** No-arg: used by Spring for the existing single-assessment routes */
    public TestCaseReviewController() {}

    /** Path-aware: used by UnifiedAssessmentController for per-assessment routes */
    public TestCaseReviewController(Path inputTesters, Path inputTemplate, Path inputExam) {
        this.inputTesters  = inputTesters;
        this.inputTemplate = inputTemplate;
        this.inputExam     = inputExam;
    }

    // ── Resolve helpers ──
    private Path resolveInputTesters()  { return inputTesters  != null ? inputTesters  : PathConfig.INPUT_TESTERS; }
    private Path resolveInputTemplate() { return inputTemplate != null ? inputTemplate : PathConfig.INPUT_TEMPLATE; }
    private Path resolveInputExam()     { return inputExam     != null ? inputExam     : ExamPaperParser.EXAM_DIR; }

    // =========================================================================
    // Step 2 — POST /parse-exam-marks
    // =========================================================================

    /**
     * Reads exam PDF (if present) and returns detected question marks.
     * If no PDF found, returns empty marks map so frontend shows manual entry.
     *
     * Response: {
     *   "success": true,
     *   "hasPdf": true,
     *   "pdfName": "exam.pdf",
     *   "marks": { "Q1a": 3, "Q1b": 2, "Q2a": 5, "Q2b": 5, "Q3": 10 },
     *   "totalMarks": 25,
     *   "message": "Extracted from exam PDF"
     * }
     */
    @PostMapping("/parse-exam-marks")
    public ResponseEntity<Map<String, Object>> parseExamMarks() {
        Map<String, Object> response = new LinkedHashMap<>();
        try {
            // Exam PDF is mandatory — fail fast if not present
            Path pdfPath = findExamPdf();
            if (pdfPath == null) {
                response.put("success", false);
                response.put("hasPdf",  false);
                response.put("message", "Exam paper PDF not found. "
                        + "Please upload it in the Upload step.");
                return ResponseEntity.badRequest().body(response);
            }

            // Parse marks from PDF via LLM
            ExamPaperParser parser = (inputExam != null)
                ? ExamPaperParser.fromEnvironment(inputExam)
                : ExamPaperParser.fromEnvironment();
                
            Map<String, Integer> marks = parser.extractMarkWeights();

            if (marks.isEmpty()) {
                response.put("success", false);
                response.put("hasPdf",  true);
                response.put("pdfName", pdfPath.getFileName().toString());
                response.put("message", "PDF found but no question marks could be extracted. "
                        + "Check that the PDF is text-based (not a scanned image).");
                return ResponseEntity.ok(response);
            }

            int total = marks.values().stream().mapToInt(Integer::intValue).sum();

            response.put("success",    true);
            response.put("hasPdf",     true);
            response.put("pdfName",    pdfPath.getFileName().toString());
            response.put("marks",      new LinkedHashMap<>(marks));
            response.put("totalMarks", total);
            response.put("message",    "Marks extracted from exam PDF — please verify before continuing.");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Mark parsing failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // =========================================================================
    // Step 3 — POST /generate-testers
    // =========================================================================

    /**
     * Generates *Tester.java source for each question using the confirmed marks.
     * Returns raw source strings — does NOT write any files yet.
     *
     * Request body: { "marks": { "Q1a": 3, "Q1b": 2, "Q2a": 5 } }
     *
     * Response: {
     *   "success": true,
     *   "testers": {
     *     "Q1a": { "filename": "Q1aTester.java", "source": "import java.util.*;\n..." },
     *     "Q1b": { "filename": "Q1bTester.java", "source": "..." }
     *   }
     * }
     */
    @PostMapping("/generate-testers")
    public ResponseEntity<Map<String, Object>> generateTesters(
            @RequestBody Map<String, Object> body) {

        Map<String, Object> response = new LinkedHashMap<>();
        try {
            // Read confirmed marks from request body
            @SuppressWarnings("unchecked")
            Map<String, Object> rawMarks = (Map<String, Object>) body.get("marks");
            if (rawMarks == null || rawMarks.isEmpty()) {
                response.put("success", false);
                response.put("message", "No marks provided in request body.");
                return ResponseEntity.badRequest().body(response);
            }

            Map<String, Integer> marks = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : rawMarks.entrySet()) {
                try { marks.put(e.getKey(), Integer.parseInt(e.getValue().toString())); }
                catch (NumberFormatException ignored) { marks.put(e.getKey(), 1); }
            }

            // Parse template specs
            Path templateZip = findTemplateZip();
            if (templateZip == null) {
                response.put("success", false);
                response.put("message", "Template ZIP not found. Upload the template first.");
                return ResponseEntity.badRequest().body(response);
            }

            TemplateTestSpecBuilder specBuilder = new TemplateTestSpecBuilder();
            Map<String, QuestionSpec> specs = specBuilder.buildQuestionSpecs(templateZip);

            if (specs.isEmpty()) {
                response.put("success", false);
                response.put("message", "No question .java files found in template ZIP.");
                return ResponseEntity.badRequest().body(response);
            }

            // Generate tester source per question
            LLMTestOracle   oracle    = LLMTestOracle.fromEnvironment();
            TesterGenerator generator = new TesterGenerator(oracle);

            Map<String, Object> testers = new LinkedHashMap<>();

            for (Map.Entry<String, QuestionSpec> entry : specs.entrySet()) {
                String       questionId = entry.getKey();
                QuestionSpec spec       = entry.getValue();
                int          numTests   = Math.max(1, marks.getOrDefault(questionId, 1));

                System.out.println("  🤖 Generating " + questionId + "Tester.java ("
                        + numTests + " test cases)...");

                String source   = generator.generate(questionId, spec, numTests);
                String filename = questionId + "Tester.java";

                Map<String, Object> testerData = new LinkedHashMap<>();
                testerData.put("filename", filename);
                testerData.put("source",   source);
                testers.put(questionId, testerData);

                System.out.println("  ✅ " + filename + " generated");
            }

            response.put("success", true);
            response.put("testers", testers);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Generation failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // =========================================================================
    // Step 4 — POST /save-testers
    // =========================================================================

    /**
     * Writes each tester source string to resources/input/testers/.
     * Called when examiner clicks "Save" after editing, and again on "Confirm & Grade".
     * Overwrites any existing file of the same name.
     *
     * Request body: {
     *   "testers": {
     *     "Q1a": "import java.util.*;\n...",
     *     "Q1b": "import java.util.*;\n..."
     *   }
     * }
     *
     * Response: { "success": true, "saved": ["Q1aTester.java", "Q1bTester.java"] }
     */
    @PostMapping("/save-testers")
    public ResponseEntity<Map<String, Object>> saveTesters(
            @RequestBody Map<String, Object> body) {

        Map<String, Object> response = new LinkedHashMap<>();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> testers = (Map<String, Object>) body.get("testers");
            if (testers == null || testers.isEmpty()) {
                response.put("success", false);
                response.put("message", "No tester sources in request body.");
                return ResponseEntity.badRequest().body(response);
            }

            Path testersDir = resolveInputTesters();
            Files.createDirectories(testersDir);

            List<String> saved = new ArrayList<>();

            for (Map.Entry<String, Object> entry : testers.entrySet()) {
                String questionId = entry.getKey();
                String source     = entry.getValue().toString();
                String filename   = questionId + "Tester.java";
                Path   testerFile = testersDir.resolve(filename);

                Files.writeString(testerFile, source,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                saved.add(filename);
                System.out.println("  💾 Saved: " + testerFile.toAbsolutePath());
            }

            response.put("success", true);
            response.put("saved",   saved);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Save failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Path findTemplateZip() {
        try (var stream = Files.list(resolveInputTemplate())) {
            return stream.filter(p -> p.toString().endsWith(".zip"))
                         .findFirst().orElse(null);
        } catch (Exception e) { return null; }
    }

    private Path findExamPdf() {
        Path examDir = resolveInputExam();
        if (!Files.exists(examDir)) {
            try { Files.createDirectories(examDir); } catch (Exception ignored) {}
            return null;
        }
        try (var stream = Files.list(examDir)) {
            return stream.filter(p -> p.toString().toLowerCase().endsWith(".pdf"))
                         .findFirst().orElse(null);
        } catch (Exception e) { return null; }
    }
}