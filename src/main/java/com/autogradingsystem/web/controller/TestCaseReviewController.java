package com.autogradingsystem.web.controller;

import com.autogradingsystem.PathConfig;
import com.autogradingsystem.testcasegenerator.model.QuestionSpec;
import com.autogradingsystem.testcasegenerator.service.ExamPaperParser;
import com.autogradingsystem.testcasegenerator.service.LLMTestOracle;
import com.autogradingsystem.testcasegenerator.service.ScriptTesterGenerator;
import com.autogradingsystem.testcasegenerator.service.TemplateTestSpecBuilder;
import com.autogradingsystem.testcasegenerator.service.TesterGenerator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.*;
import java.util.*;

/**
 * TestCaseReviewController - Handles the examiner review workflow.
 */
@RestController
public class TestCaseReviewController {

    private Path inputTesters;
    private Path inputTemplate;
    private Path inputExam;

    public TestCaseReviewController() {}

    public TestCaseReviewController(Path inputTesters, Path inputTemplate, Path inputExam) {
        this.inputTesters = inputTesters;
        this.inputTemplate = inputTemplate;
        this.inputExam = inputExam;
    }

    private Path resolveInputTesters() {
        return inputTesters != null ? inputTesters : PathConfig.INPUT_TESTERS;
    }

    private Path resolveInputTemplate() {
        return inputTemplate != null ? inputTemplate : PathConfig.INPUT_TEMPLATE;
    }

    private Path resolveInputExam() {
        return inputExam != null ? inputExam : ExamPaperParser.EXAM_DIR;
    }

    @PostMapping("/parse-exam-marks")
    public ResponseEntity<Map<String, Object>> parseExamMarks() {
        Map<String, Object> response = new LinkedHashMap<>();
        try {
            Path pdfPath = findExamPdf();
            if (pdfPath == null) {
                response.put("success", false);
                response.put("hasPdf", false);
                response.put("message", "Exam paper PDF not found. Please upload it in the Upload step.");
                return ResponseEntity.badRequest().body(response);
            }

            ExamPaperParser parser = (inputExam != null)
                    ? ExamPaperParser.fromEnvironment(inputExam)
                    : ExamPaperParser.fromEnvironment();

            Map<String, Integer> marks = parser.extractMarkWeights();
            if (marks.isEmpty()) {
                response.put("success", false);
                response.put("hasPdf", true);
                response.put("pdfName", pdfPath.getFileName().toString());
                response.put("message", "PDF found but no question marks could be extracted. Check that the PDF is text-based (not a scanned image).");
                return ResponseEntity.ok(response);
            }

            int total = marks.values().stream().mapToInt(Integer::intValue).sum();
            response.put("success", true);
            response.put("hasPdf", true);
            response.put("pdfName", pdfPath.getFileName().toString());
            response.put("marks", new LinkedHashMap<>(marks));
            response.put("totalMarks", total);
            response.put("message", "Marks extracted from exam PDF - please verify before continuing.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Mark parsing failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

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
                try {
                    marks.put(e.getKey(), Integer.parseInt(e.getValue().toString()));
                } catch (NumberFormatException ignored) {
                    marks.put(e.getKey(), 1);
                }
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

            Map<String, String> descriptions = new LinkedHashMap<>();
            try {
                ExamPaperParser descParser = (inputExam != null)
                        ? ExamPaperParser.fromEnvironment(inputExam)
                        : ExamPaperParser.fromEnvironment();
                descriptions = new LinkedHashMap<>(descParser.extractQuestionDescriptions());

                for (Map.Entry<String, QuestionSpec> entry : specs.entrySet()) {
                    String desc = descriptions.get(entry.getKey());
                    if (desc != null && !desc.isBlank()) {
                        entry.getValue().setDescription(desc);
                        System.out.println("  [DESC] Injected description for " + entry.getKey());
                    }
                }
            } catch (Exception descEx) {
                System.err.println("  [WARN] Description extraction failed: " + descEx.getMessage());
            }

            Set<String> scriptQuestions = new LinkedHashSet<>();
            try {
                ExamPaperParser scriptParser = (inputExam != null)
                        ? ExamPaperParser.fromEnvironment(inputExam)
                        : ExamPaperParser.fromEnvironment();
                scriptQuestions = scriptParser.extractScriptQuestions();
                if (!scriptQuestions.isEmpty()) {
                    System.out.println("  [SCRIPT] Detected script/classpath questions: " + scriptQuestions);
                }
            } catch (Exception scriptEx) {
                System.err.println("  [WARN] Script question detection failed: " + scriptEx.getMessage());
            }

            LLMTestOracle oracle = LLMTestOracle.fromEnvironment();
            TesterGenerator generator = new TesterGenerator(oracle);
            ScriptTesterGenerator scriptGenerator = new ScriptTesterGenerator();

            Map<String, Object> testers = new LinkedHashMap<>();

            for (Map.Entry<String, QuestionSpec> entry : specs.entrySet()) {
                String questionId = entry.getKey();
                QuestionSpec spec = entry.getValue();
                int numTests = Math.max(1, marks.getOrDefault(questionId, 1));
                String filename = questionId + "Tester.java";
                Path testerPath = resolveInputTesters().resolve(filename);

                if (scriptQuestions.contains(questionId)) {
                    if (Files.exists(testerPath)) {
                        String existingSource = Files.readString(testerPath);
                        Map<String, Object> testerData = new LinkedHashMap<>();
                        testerData.put("filename", filename);
                        testerData.put("source", existingSource);
                        testerData.put("note", "Hand-written tester (script question)");
                        testers.put(questionId, testerData);
                        System.out.println("  [SCRIPT] Loaded existing " + filename);
                    } else {
                        String source = scriptGenerator.generate(
                                questionId,
                                numTests,
                                descriptions.get(questionId),
                                templateZip);
                        Map<String, Object> testerData = new LinkedHashMap<>();
                        testerData.put("filename", filename);
                        testerData.put("source", source);
                        testerData.put("note", "Auto-generated script/classpath tester fallback");
                        testers.put(questionId, testerData);
                        System.out.println("  [SCRIPT] Generated fallback " + filename);
                    }
                    continue;
                }

                System.out.println("  [LLM] Generating " + filename + " (" + numTests + " test cases)...");
                String source = generator.generate(questionId, spec, numTests);
                Map<String, Object> testerData = new LinkedHashMap<>();
                testerData.put("filename", filename);
                testerData.put("source", source);
                testers.put(questionId, testerData);
            }

            for (String questionId : scriptQuestions) {
                if (testers.containsKey(questionId)) continue;

                String filename = questionId + "Tester.java";
                Path testerPath = resolveInputTesters().resolve(filename);
                if (Files.exists(testerPath)) {
                    String existingSource = Files.readString(testerPath);
                    Map<String, Object> testerData = new LinkedHashMap<>();
                    testerData.put("filename", filename);
                    testerData.put("source", existingSource);
                    testerData.put("note", "Hand-written tester (script folder task)");
                    testers.put(questionId, testerData);
                    System.out.println("  [SCRIPT] Loaded existing " + filename + " for folder task");
                    continue;
                }

                int numTests = Math.max(1, marks.getOrDefault(questionId, 1));
                String source = scriptGenerator.generate(
                        questionId,
                        numTests,
                        descriptions.get(questionId),
                        templateZip);
                Map<String, Object> testerData = new LinkedHashMap<>();
                testerData.put("filename", filename);
                testerData.put("source", source);
                testerData.put("note", "Auto-generated script/classpath tester fallback");
                testers.put(questionId, testerData);
                System.out.println("  [SCRIPT] Generated fallback " + filename + " for folder task");
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
                String source = entry.getValue().toString();
                String filename = questionId + "Tester.java";
                Path testerFile = testersDir.resolve(filename);

                Files.writeString(testerFile, source,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                saved.add(filename);
                System.out.println("  Saved: " + testerFile.toAbsolutePath());
            }

            response.put("success", true);
            response.put("saved", saved);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Save failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    private Path findTemplateZip() {
        try (var stream = Files.list(resolveInputTemplate())) {
            return stream.filter(p -> p.toString().endsWith(".zip"))
                    .findFirst().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private Path findExamPdf() {
        Path examDir = resolveInputExam();
        if (!Files.exists(examDir)) {
            try {
                Files.createDirectories(examDir);
            } catch (Exception ignored) {}
            return null;
        }
        try (var stream = Files.list(examDir)) {
            return stream.filter(p -> p.toString().toLowerCase().endsWith(".pdf"))
                    .findFirst().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
