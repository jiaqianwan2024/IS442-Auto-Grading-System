package com.autogradingsystem.testcasegenerator.service;

import com.autogradingsystem.testcasegenerator.model.QuestionSpec;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

/**
 * TemplateTestSpecBuilder - Builds inputs needed by TestCaseGeneratorController.
 *
 * PACKAGE: com.autogradingsystem.testcasegenerator.service
 *
 * v3.5: Now collects ALL .java files per question folder — not just the main
 * question file. Supporting files (Shape.java, Rectangle.java, etc.) are stored
 * in QuestionSpec.supportingSourceFiles so the LLM knows about abstract classes,
 * concrete subclasses, and helper classes when generating test cases.
 */
public class TemplateTestSpecBuilder {

    private final QuestionSpecParser specParser = new QuestionSpecParser();
    private final ExamPaperParser    examParser = ExamPaperParser.fromEnvironment();

    // Pattern matching question file names: Q1a.java, Q2b.java, Q3.java
    private static final Pattern QUESTION_FILE = Pattern.compile(
            "^(Q\\d+[a-zA-Z]?)\\.java$", Pattern.CASE_INSENSITIVE);

    // -------------------------------------------------------------------------
    // Build question specs from template ZIP
    // -------------------------------------------------------------------------

    public Map<String, QuestionSpec> buildQuestionSpecs(Path templateZip) throws IOException {
        Map<String, QuestionSpec> specs = new LinkedHashMap<>();
        if (templateZip == null || !Files.exists(templateZip)) return specs;

        Path tempDir = Files.createTempDirectory("is442_template_");
        try {
            extractZip(templateZip, tempDir);

            // Group all relevant files by their parent folder (Q1, Q2, Q3, ...)
            // Includes .java source files AND .txt/.csv data files (e.g. persons.txt, students.txt)
            // so the LLM can compute expected values for file-based questions like getAverageAge.
            // Map: folderName -> Map<filename, Path>
            Map<String, Map<String, Path>> byFolder = new LinkedHashMap<>();
            try (var walk = Files.walk(tempDir)) {
                walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return n.endsWith(".java") || n.endsWith(".txt") || n.endsWith(".csv");
                    })
                    .filter(p -> !p.toString().contains("__MACOSX"))
                    .filter(p -> !p.getFileName().toString().startsWith("."))
                    .forEach(p -> {
                        String folder = p.getParent().getFileName().toString();
                        byFolder
                            .computeIfAbsent(folder, k -> new LinkedHashMap<>())
                            .put(p.getFileName().toString(), p);
                    });
            }

            // For each folder, find the main question file and collect supporting files
            for (Map.Entry<String, Map<String, Path>> folderEntry : byFolder.entrySet()) {
                Map<String, Path> files = folderEntry.getValue();

                for (Map.Entry<String, Path> fileEntry : files.entrySet()) {
                    String fname = fileEntry.getKey();
                    Path   fpath = fileEntry.getValue();

                    Matcher m = QUESTION_FILE.matcher(fname);
                    if (!m.matches()) continue; // skip supporting files in main loop

                    String questionId = normaliseId(m.group(1));
                    try {
                        QuestionSpec spec = specParser.parse(fpath);

                        // Attach all OTHER files in the same folder as supporting context:
                        //   - .java files: supporting classes (Shape, DataException, etc.)
                        //   - .txt/.csv data files (persons.txt, students.txt) — the LLM
                        //     needs these to compute expected values for file-based questions.
                        for (Map.Entry<String, Path> sibling : files.entrySet()) {
                            if (sibling.getKey().equals(fname)) continue; // skip self
                            if (sibling.getKey().endsWith(".class")) continue; // skip .class
                            try {
                                String siblingSource = Files.readString(sibling.getValue());
                                spec.addSupportingFile(sibling.getKey(), siblingSource);
                                System.out.println("   [data] Attached: "
                                        + sibling.getKey() + " (" + siblingSource.length() + " chars)");
                            } catch (IOException e) {
                                System.err.println("[warn] Could not read " + sibling.getKey());
                            }
                        }
