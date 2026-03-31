package com.autogradingsystem.testcasegenerator.controller;

import com.autogradingsystem.PathConfig;
import com.autogradingsystem.testcasegenerator.model.QuestionSpec;
import com.autogradingsystem.testcasegenerator.service.ExamPaperParser;
import com.autogradingsystem.testcasegenerator.service.ScriptTesterGenerator;
import com.autogradingsystem.testcasegenerator.service.TesterGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * TestCaseGeneratorController - orchestrates tester generation when no saved
 * tester files exist yet.
 */
public class TestCaseGeneratorController {

    private final TesterGenerator testerGenerator;
    private final ScriptTesterGenerator scriptTesterGenerator;
    private final Path testersDir;
    private final Path examDir;
    private final Path templateDir;

    public TestCaseGeneratorController() {
        this(PathConfig.INPUT_TESTERS, ExamPaperParser.EXAM_DIR, PathConfig.INPUT_TEMPLATE);
    }

    public TestCaseGeneratorController(Path testersDir, Path examDir) {
        this(testersDir, examDir, PathConfig.INPUT_TEMPLATE);
    }

    public TestCaseGeneratorController(Path testersDir, Path examDir, Path templateDir) {
        this.testerGenerator = new TesterGenerator();
        this.scriptTesterGenerator = new ScriptTesterGenerator();
        this.testersDir = testersDir != null ? testersDir : PathConfig.INPUT_TESTERS;
        this.examDir = examDir != null ? examDir : ExamPaperParser.EXAM_DIR;
        this.templateDir = templateDir != null ? templateDir : PathConfig.INPUT_TEMPLATE;
    }

    public boolean generateAll(Map<String, QuestionSpec> specs,
                               Map<String, Integer> weights) throws IOException {
        Files.createDirectories(testersDir);
        clearTestersDir(testersDir);
        return generateInto(specs, weights, testersDir);
    }

    public boolean generateIfNeeded(Map<String, QuestionSpec> specs,
                                    Map<String, Integer> weights) throws IOException {
        Files.createDirectories(testersDir);
        if (savedTestersExist(testersDir)) {
            System.out.println("[TestGen] Saved testers found on disk - skipping generation.");
            return true;
        }

        System.out.println("[TestGen] No testers found - generating from template + exam paper.");
        return generateInto(specs, weights, testersDir);
    }

    private boolean savedTestersExist(Path dir) {
        if (!Files.isDirectory(dir)) return false;
        try (Stream<Path> files = Files.list(dir)) {
            return files.anyMatch(p -> p.getFileName().toString().endsWith("Tester.java"));
        } catch (IOException e) {
            return false;
        }
    }

    private void clearTestersDir(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith("Tester.java"))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            System.err.println("[TestGen] Could not delete " + p + ": " + e.getMessage());
                        }
                    });
        }
    }

    private boolean generateInto(Map<String, QuestionSpec> specs,
                                 Map<String, Integer> weights,
                                 Path dir) throws IOException {
        if (specs == null || specs.isEmpty()) {
            System.err.println("[TestGen] No question specs provided.");
            return false;
        }

        Map<String, String> descriptions = loadDescriptions();
        for (Map.Entry<String, QuestionSpec> entry : specs.entrySet()) {
            String desc = descriptions.get(entry.getKey());
            if (desc != null && !desc.isBlank()) {
                entry.getValue().setDescription(desc);
            }
        }

        Set<String> scriptQuestions = loadScriptQuestions();

        int written = 0;
        Path templateZip = findTemplateZip();
        for (Map.Entry<String, QuestionSpec> entry : specs.entrySet()) {
            String questionId = entry.getKey();
            QuestionSpec spec = entry.getValue();
            int weight = weights != null ? weights.getOrDefault(questionId, 1) : 1;

            try {
                String source = scriptQuestions.contains(questionId)
                        ? scriptTesterGenerator.generate(questionId, weight, descriptions.get(questionId), templateZip)
                        : testerGenerator.generate(questionId, spec, weight);
                Files.writeString(dir.resolve(questionId + "Tester.java"), source);
                written++;
                System.out.println("[TestGen] Wrote " + questionId + "Tester.java");
            } catch (Exception e) {
                System.err.println("[TestGen] Failed for " + questionId + ": " + e.getMessage());
            }
        }

        for (String questionId : scriptQuestions) {
            Path output = dir.resolve(questionId + "Tester.java");
            if (Files.exists(output)) continue;

            int weight = weights != null ? weights.getOrDefault(questionId, 1) : 1;
            try {
                String source = scriptTesterGenerator.generate(
                        questionId,
                        weight,
                        descriptions.get(questionId),
                        templateZip);
                Files.writeString(output, source);
                written++;
                System.out.println("[TestGen] Wrote " + output.getFileName() + " (script folder task)");
            } catch (Exception e) {
                System.err.println("[TestGen] Failed for script question " + questionId + ": " + e.getMessage());
            }
        }

        System.out.println("[TestGen] Total testers written: " + written);
        return written > 0;
    }

    private Map<String, String> loadDescriptions() {
        try {
            ExamPaperParser parser = ExamPaperParser.fromEnvironment(examDir);
            return new LinkedHashMap<>(parser.extractQuestionDescriptions());
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private Set<String> loadScriptQuestions() {
        try {
            ExamPaperParser parser = ExamPaperParser.fromEnvironment(examDir);
            return new LinkedHashSet<>(parser.extractScriptQuestions());
        } catch (Exception e) {
            return new LinkedHashSet<>();
        }
    }

    private Path findTemplateZip() {
        if (!Files.isDirectory(templateDir)) return null;
        try (Stream<Path> files = Files.list(templateDir)) {
            return files.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".zip"))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }
}
