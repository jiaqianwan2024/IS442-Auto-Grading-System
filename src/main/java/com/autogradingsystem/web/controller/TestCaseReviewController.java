package com.autogradingsystem.web.controller;

import com.autogradingsystem.testcasegenerator.model.QuestionSpec;
import com.autogradingsystem.testcasegenerator.service.ExamPaperParser;
import com.autogradingsystem.testcasegenerator.service.LLMTestOracle;
import com.autogradingsystem.testcasegenerator.service.TemplateTestSpecBuilder;
import com.autogradingsystem.testcasegenerator.service.TesterAugmentor;
import com.autogradingsystem.testcasegenerator.service.TesterGenerator;
import com.autogradingsystem.PathConfig;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.*;
import java.util.*;

/**
 * TestCaseReviewController
 *
 * WORKFLOW A — Standard 4-step flow (unchanged):
 *   POST /parse-exam-marks   → LLM reads exam PDF, returns Q→marks
 *   POST /generate-testers   → generates *Tester.java source via LLM
 *   POST /save-testers       → writes approved sources to disk
 *
 * WORKFLOW B — Test Case Studio (opt-in, separate tab):
 *   POST /analyze-template   → parse template ZIP, return question structure
 *   POST /studio-augment     → accepts template ZIP + optional existing tester
 *                              files + marks; generates additional test cases on top
 *                              of existing ones (or from scratch), targeting
 *                              mark-count test cases per question
 *   POST /save-testers       → shared; writes approved sources to disk
 */
@RestController
public class TestCaseReviewController {

    // =========================================================================
    // WORKFLOW A — POST /parse-exam-marks
    // =========================================================================

    @PostMapping("/parse-exam-marks")
    public ResponseEntity<Map<String, Object>> parseExamMarks() {
        Map<String, Object> response = new LinkedHashMap<>();
        try {
            Path pdfPath = findExamPdf();
            if (pdfPath == null) {
                response.put("success", false);
                response.put("hasPdf",  false);
                response.put("message", "Exam paper PDF not found. Please upload it in the Upload step.");
                return ResponseEntity.badRequest().body(response);
            }

            ExamPaperParser parser = ExamPaperParser.fromEnvironment();
            Map<String, Integer> marks = parser.extractMarkWeights();

            if (marks.isEmpty()) {
                response.put("success", false);
                response.put("hasPdf",  true);
                response.put("pdfName", pdfPath.getFileName().toString());
                response.put("message", "PDF found but no question marks could be extracted.");
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
    // WORKFLOW A — POST /generate-testers
    // =========================================================================

    @PostMapping("/generate-testers")
    public ResponseEntity<Map<String, Object>> generateTesters(
            @RequestBody Map<String, Object> body) {

        Map<String, Object> response = new LinkedHashMap<>();
        try {
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

            LLMTestOracle   oracle    = LLMTestOracle.fromEnvironment();
            TesterGenerator generator = new TesterGenerator(oracle);
            Map<String, Object> testers = new LinkedHashMap<>();

            for (Map.Entry<String, QuestionSpec> entry : specs.entrySet()) {
                String       questionId = entry.getKey();
                QuestionSpec spec       = entry.getValue();
                int          numTests   = Math.max(1, marks.getOrDefault(questionId, 1));

                System.out.println("  🤖 Generating " + questionId + "Tester.java (" + numTests + " test cases)...");
                String source = generator.generate(questionId, spec, numTests);

                Map<String, Object> testerData = new LinkedHashMap<>();
                testerData.put("filename", questionId + "Tester.java");
                testerData.put("source",   source);
                testers.put(questionId, testerData);
                System.out.println("  ✅ " + questionId + "Tester.java generated");
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
    // WORKFLOW A & B — POST /save-testers
    // =========================================================================

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

            Path testersDir = PathConfig.INPUT_TESTERS;
            Files.createDirectories(testersDir);
            List<String> saved = new ArrayList<>();

            for (Map.Entry<String, Object> entry : testers.entrySet()) {
                String questionId = entry.getKey();
                String source     = entry.getValue().toString();
                Path   testerFile = testersDir.resolve(questionId + "Tester.java");
                Files.writeString(testerFile, source,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                saved.add(questionId + "Tester.java");
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
    // WORKFLOW B — POST /analyze-template
    // =========================================================================

    @PostMapping("/analyze-template")
    public ResponseEntity<Map<String, Object>> analyzeTemplate(
            @RequestParam("template") MultipartFile templateFile) {

        Map<String, Object> response = new LinkedHashMap<>();
        Path tempZip = null;
        try {
            if (templateFile == null || templateFile.isEmpty()) {
                response.put("success", false);
                response.put("message", "No template file uploaded.");
                return ResponseEntity.badRequest().body(response);
            }

            tempZip = Files.createTempFile("studio_template_", ".zip");
            templateFile.transferTo(tempZip.toFile());

            TemplateTestSpecBuilder specBuilder = new TemplateTestSpecBuilder();
            Map<String, QuestionSpec> specs = specBuilder.buildQuestionSpecs(tempZip);

            if (specs.isEmpty()) {
                response.put("success", false);
                response.put("message", "No question files (Q*.java) found in the uploaded ZIP.");
                return ResponseEntity.badRequest().body(response);
            }

            Map<String, Object> questions = new LinkedHashMap<>();
            for (Map.Entry<String, QuestionSpec> entry : specs.entrySet()) {
                String questionId = entry.getKey();
                QuestionSpec spec = entry.getValue();
                Map<String, Object> qData = new LinkedHashMap<>();
                qData.put("className", spec.getClassName());

                List<Map<String, Object>> methods = new ArrayList<>();
                for (QuestionSpec.MethodSpec m : spec.getMethods()) {
                    if ("main".equals(m.getName())) continue;
                    Map<String, Object> mData = new LinkedHashMap<>();
                    mData.put("name",       m.getName());
                    mData.put("returnType", m.getReturnType());
                    mData.put("isStatic",   m.isStatic());
                    List<Map<String, String>> params = new ArrayList<>();
                    for (QuestionSpec.ParamSpec p : m.getParams()) {
                        params.add(Map.of("type", p.getType(), "name", p.getName()));
                    }
                    mData.put("params", params);
                    methods.add(mData);
                }
                qData.put("methods",            methods);
                qData.put("hasSupportingFiles", !spec.getSupportingSourceFiles().isEmpty());
                qData.put("supportingFiles",    new ArrayList<>(spec.getSupportingSourceFiles().keySet()));
                questions.put(questionId, qData);
            }

            response.put("success",   true);
            response.put("questions", questions);
            response.put("message",   specs.size() + " question(s) found in template.");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Template analysis failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        } finally {
            if (tempZip != null) {
                try { Files.deleteIfExists(tempZip); } catch (Exception ignored) {}
            }
        }
    }

    // =========================================================================
    // WORKFLOW B — POST /studio-augment
    //
    // Multipart fields:
    //   template      (required) — template ZIP with Q*.java source files
    //   marksJson     (required) — {"Q1a":6,"Q1b":6,"Q2a":10,"Q2b":10,"Q3":4}
    //   tester_Q1a    (optional) — existing Q1aTester.java to augment
    //   tester_Q1b    (optional) — existing Q1bTester.java to augment
    //   ... one optional field per question, named tester_<questionId>
    //
    // Per question logic:
    //   existing tester uploaded  → count its test cases → generate (marks−existing) more
    //   no tester uploaded        → generate marks-count test cases from scratch
    //   existing count >= marks   → return unchanged, nothing generated
    // =========================================================================

    @PostMapping("/studio-augment")
    public ResponseEntity<Map<String, Object>> studioAugment(
            @RequestParam("template")  MultipartFile templateFile,
            @RequestParam("marksJson") String marksJson,
            @RequestParam Map<String, MultipartFile> allParams) {

        Map<String, Object> response = new LinkedHashMap<>();
        Path tempZip = null;
        try {
            if (templateFile == null || templateFile.isEmpty()) {
                response.put("success", false);
                response.put("message", "No template file uploaded.");
                return ResponseEntity.badRequest().body(response);
            }

            // Parse marks JSON
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> rawMarks = mapper.readValue(marksJson, Map.class);
            Map<String, Integer> marks = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : rawMarks.entrySet()) {
                try { marks.put(e.getKey(), Integer.parseInt(e.getValue().toString())); }
                catch (NumberFormatException ignored) { marks.put(e.getKey(), 1); }
            }

            // Save template to temp file
            tempZip = Files.createTempFile("studio_aug_", ".zip");
            templateFile.transferTo(tempZip.toFile());

            TemplateTestSpecBuilder specBuilder = new TemplateTestSpecBuilder();
            Map<String, QuestionSpec> specs = specBuilder.buildQuestionSpecs(tempZip);

            if (specs.isEmpty()) {
                response.put("success", false);
                response.put("message", "No question files found in the uploaded template ZIP.");
                return ResponseEntity.badRequest().body(response);
            }

            TesterAugmentor augmentor = new TesterAugmentor();
            Map<String, Object> testers = new LinkedHashMap<>();

            for (Map.Entry<String, QuestionSpec> entry : specs.entrySet()) {
                String       questionId  = entry.getKey();
                QuestionSpec spec        = entry.getValue();
                int          targetCount = Math.max(1, marks.getOrDefault(questionId, 1));

                // Read existing tester if uploaded (field: tester_Q1a, tester_Q2b, etc.)
                String existingSource = null;
                MultipartFile existingFile = allParams.get("tester_" + questionId);
                if (existingFile != null && !existingFile.isEmpty()) {
                    existingSource = new String(existingFile.getBytes());
                    System.out.println("  📂 [Studio] " + questionId
                            + ": existing tester uploaded (" + existingSource.length() + " chars)");
                }

                int existingCount = augmentor.countTestCases(existingSource);
                String augmented  = augmentor.augment(questionId, spec, existingSource, targetCount);
                int addedCount    = targetCount; // always the full mark count is added

                Map<String, Object> testerData = new LinkedHashMap<>();
                testerData.put("filename",      questionId + "Tester.java");
                testerData.put("source",        augmented);
                testerData.put("existingCount", existingCount);
                testerData.put("addedCount",    addedCount);
                testerData.put("totalCount",    augmentor.countTestCases(augmented));
                testers.put(questionId, testerData);

                System.out.println("  ✅ [Studio] " + questionId + ": "
                        + existingCount + " existing + " + addedCount + " added = "
                        + testerData.get("totalCount") + " total (target: " + targetCount + ")");
            }

            response.put("success", true);
            response.put("testers", testers);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Augmentation failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        } finally {
            if (tempZip != null) {
                try { Files.deleteIfExists(tempZip); } catch (Exception ignored) {}
            }
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Path findTemplateZip() {
        try (var stream = Files.list(PathConfig.INPUT_TEMPLATE)) {
            return stream.filter(p -> p.toString().endsWith(".zip"))
                         .findFirst().orElse(null);
        } catch (Exception e) { return null; }
    }

    private Path findExamPdf() {
        Path examDir = ExamPaperParser.EXAM_DIR;
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
