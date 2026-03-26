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

            // Group ALL files (not just .java) by their parent folder (Q1, Q2, Q3, ...)
            // This ensures data files like persons.txt, students.txt are captured too.
            // Map: folderName → Map<filename, Path>
            Map<String, Map<String, Path>> byFolder = new LinkedHashMap<>();
            try (var walk = Files.walk(tempDir)) {
                walk.filter(Files::isRegularFile)
                    .filter(p -> !p.toString().contains("__MACOSX"))
                    .filter(p -> !p.toString().contains(".DS_Store"))
                    .filter(p -> !p.toString().contains("/api/"))   // skip javadoc
                    .filter(p -> !p.toString().endsWith(".class"))  // skip bytecode
                    .filter(p -> !p.toString().endsWith(".html"))
                    .filter(p -> !p.toString().endsWith(".js"))
                    .filter(p -> !p.toString().endsWith(".css"))
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

                        // Attach all OTHER files in the same folder as supporting context
                        for (Map.Entry<String, Path> sibling : files.entrySet()) {
                            if (sibling.getKey().equals(fname)) continue; // skip self
                            try {
                                if (sibling.getKey().endsWith(".java")) {
                                    // Java source files: attach as supporting source
                                    String siblingSource = Files.readString(sibling.getValue());
                                    spec.addSupportingFile(sibling.getKey(), siblingSource);
                                } else {
                                    // Data files (txt, csv, etc.): attach as data file content
                                    // The LLM needs to see these to generate meaningful test cases
                                    String content = Files.readString(sibling.getValue());
                                    spec.addDataFile(sibling.getKey(), content);
                                }
                            } catch (IOException e) {
                                System.err.println("⚠️  Could not read " + sibling.getKey());
                            }
                        }

                        specs.put(questionId, spec);
                    } catch (IOException e) {
                        System.err.println("⚠️  Could not parse " + fname + ": " + e.getMessage());
                    }
                }
            }

        } finally {
            deleteRecursively(tempDir);
        }

        return specs;
    }

    // -------------------------------------------------------------------------
    // Read score weights from exam paper PDF via LLM
    // -------------------------------------------------------------------------

    public Map<String, Integer> readScoreWeights() {
        Map<String, Integer> weights = examParser.extractMarkWeights();
        if (weights.isEmpty()) {
            System.out.println("  ⚠️  No mark weights from exam PDF — defaulting to 1 mark per question.");
        }
        return weights;
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String normaliseId(String raw) {
        if (raw == null || raw.length() < 2) return raw;
        StringBuilder sb = new StringBuilder();
        sb.append(Character.toUpperCase(raw.charAt(0)));
        for (int i = 1; i < raw.length(); i++) {
            char c = raw.charAt(i);
            sb.append(Character.isLetter(c) ? Character.toLowerCase(c) : c);
        }
        return sb.toString();
    }

    private void extractZip(Path zipPath, Path destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipPath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) { zis.closeEntry(); continue; }
                Path target = destDir.resolve(entry.getName()).normalize();
                if (!target.startsWith(destDir)) { zis.closeEntry(); continue; }
                Files.createDirectories(target.getParent());
                Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                zis.closeEntry();
            }
        }
    }

    private void deleteRecursively(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        } catch (IOException ignored) {}
    }
}