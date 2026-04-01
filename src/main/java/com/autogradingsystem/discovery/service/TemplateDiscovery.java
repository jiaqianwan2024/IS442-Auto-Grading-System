package com.autogradingsystem.discovery.service;

import com.autogradingsystem.discovery.model.ExamStructure;
import com.autogradingsystem.extraction.service.ZipFileProcessor;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipException;

/**
 * TemplateDiscovery - Discovers Exam Structure from Template ZIP
 *
 * PURPOSE:
 * - Automatically discovers exam structure (questions and files)
 * - No hardcoded question lists needed
 * - Scans template ZIP and extracts structure information
 */
public class TemplateDiscovery {

    /** Maximum wrapper-folder depth before we give up and throw. */
    private static final int MAX_NESTING_DEPTH = 20;

    /** Template ZIPs larger than this are rejected before extraction. */
    private static final long MAX_ZIP_BYTES = 50L * 1024 * 1024; // 50 MB

    /** Q folders with more files than this get a [TOO-MANY-FILES] warning. */
    private static final int MAX_FILES_PER_QUESTION = 50;

    // ================================================================
    // PUBLIC API
    // ================================================================

    /**
     * Discovers exam structure from template ZIP.
     *
     * @param templateZip Path to template ZIP file
     * @return ExamStructure containing all discovered questions and files
     * @throws IOException if template cannot be read or is structurally invalid
     */
    public ExamStructure discoverStructure(Path templateZip) throws IOException {

        System.out.println("   \uD83D\uDD0D Scanning Template ZIP: " + templateZip.getFileName());

        // Reject oversized ZIPs before extraction
        if (Files.size(templateZip) > MAX_ZIP_BYTES) {
            throw new IOException(
                "Template ZIP is too large (> 50 MB): " + templateZip.getFileName() + "\n" +
                "Please verify you placed the correct file in the assessment template directory."
            );
        }

        Path tempDir = Files.createTempDirectory("template-discovery");

        try {
            // Catch ZipException and surface a friendly message
            try {
                ZipFileProcessor.unzip(templateZip, tempDir);
            } catch (ZipException ze) {
                throw new IOException(
                    "Cannot read template ZIP: " + templateZip.getFileName() + "\n" +
                    "Possible causes:\n" +
                    "  1. File is password-protected\n" +
                    "  2. File is corrupted or partially downloaded\n" +
                    "  3. File is not actually a ZIP (wrong extension)\n" +
                    "Original error: " + ze.getMessage(), ze
                );
            }

            // Find the directory that contains Q folders (handles nested wrappers)
            Path rootDir = findRootDirectory(tempDir, 0);

            // Scan and collect files
            Map<String, List<String>> questionFiles = scanForQuestions(rootDir);

            if (questionFiles.isEmpty()) {
                throw new IOException(
                    "No question folders found in template!\n" +
                    "Expected folders like Q1/, Q2/, Q3/ containing .java files"
                );
            }

            // isValid() checks that at least one Q folder has a .java or .class file.
            // A Q folder with only .txt files is not a valid exam question.
            ExamStructure structure = new ExamStructure(questionFiles);
            if (!structure.isValid()) {
                List<String> emptyFolders = questionFiles.entrySet().stream()
                    .filter(e -> e.getValue().stream()
                        .noneMatch(f -> f.toLowerCase().endsWith(".java")
                                     || f.toLowerCase().endsWith(".class")))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
                throw new IOException(
                    "Template Q folders contain no .java or .class files: " + emptyFolders + "\n" +
                    "Each Q folder must have at least one .java file to grade."
                );
            }

            System.out.println("   \u2705 Template Discovery Complete. Discovered "
                + questionFiles.size() + " question folder(s).");
            return structure;

        } finally {
            deleteDirectory(tempDir);
        }
    }

    // ================================================================
    // PRIVATE HELPERS
    // ================================================================

    /**
     * Recursively finds the directory containing Q folders.
     *
     * LOGIC:
     * 1. If current dir has Q folders -> return it
     * 2. Collect real subdirs (skip __MACOSX and hidden dot-folders)
     * 3. If exactly 1 real subdir -> recurse into it
     * 4. Otherwise -> throw with a list of what was found
     *
     * Depth is capped at MAX_NESTING_DEPTH to prevent infinite recursion on
     * pathological ZIPs with circular or excessively deep folder chains.
     */
    private Path findRootDirectory(Path currentDir, int depth) throws IOException {

        if (depth > MAX_NESTING_DEPTH) {
            throw new IOException(
                "Template ZIP exceeds " + MAX_NESTING_DEPTH + " nested wrapper levels. " +
                "Please flatten the ZIP structure."
            );
        }

        if (hasQuestionFolders(currentDir)) {
            return currentDir;
        }

        List<Path> realSubdirs = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(currentDir)) {
            for (Path item : stream) {
                if (!Files.isDirectory(item)) continue;
                String name = item.getFileName().toString();
                // Skip macOS metadata folder and any hidden (dot-prefixed) folder
                if (name.equals("__MACOSX") || name.startsWith(".")) continue;
                realSubdirs.add(item);
            }
        }

        if (realSubdirs.size() == 1) {
            System.out.println("      \uD83D\uDCC2 Wrapper folder detected, going deeper: "
                + realSubdirs.get(0).getFileName());
            return findRootDirectory(realSubdirs.get(0), depth + 1);
        }

        List<String> names = realSubdirs.stream()
            .map(p -> p.getFileName().toString())
            .collect(Collectors.toList());
        throw new IOException(
            "Invalid template structure — cannot locate Q folders.\n" +
            "Found " + realSubdirs.size() + " subdirectories: " + names + "\n" +
            "Expected: Template ZIP -> (optional wrapper) -> Q1/, Q2/, Q3/"
        );
    }

    /**
     * Returns true if dir contains at least one folder matching ^Q[1-9]\d*$.
     */
    private boolean hasQuestionFolders(Path dir) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path item : stream) {
                if (Files.isDirectory(item)
                        && item.getFileName().toString().matches("^Q[1-9]\\d*$")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Scans rootDir for Q folders and collects their files recursively.
     *
     * Q FOLDER REGEX: ^Q[1-9]\d*$
     *   Accepts : Q1, Q2, Q10
     *   Rejects : Q01 (leading zero), q1 (lowercase), Q1a (letter suffix), Q0
     *
     * Leading zeros are rejected because Q01 and Q1 both parse to integer 1 in
     * QuestionComparator, causing a silent collision in the result map.
     */
    private Map<String, List<String>> scanForQuestions(Path rootDir) throws IOException {

        Map<String, List<String>> questionFiles = new TreeMap<>(new QuestionComparator());

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(rootDir)) {
            for (Path item : stream) {
                if (!Files.isDirectory(item)) continue;
                String folderName = item.getFileName().toString();
                if (!folderName.matches("^Q[1-9]\\d*$")) continue;

                List<String> files = collectFilesRecursively(item, folderName);
                Collections.sort(files);
                questionFiles.put(folderName, files);

                System.out.println("      \uD83D\uDCC1 Found Question Folder: [" + folderName
                    + "] with " + files.size() + " file(s)");
                for (String f : files) {
                    System.out.println("         \uD83D\uDCC4 Expected File: " + f);
                }
            }
        }

        return questionFiles;
    }

    /**
     * Recursively collects files from a Q folder using Files.walk().
     *
     * COLLECTS: .java, .class, .txt, .csv files
     *
     * SKIPS Q-named subfolders (Fix-T36):
     *   If a student accidentally put Q2/ inside Q1/, that Q2 folder is skipped
     *   with a [SKIP-Q-FOLDER] warning. It is a separate question, not a nested file.
     *
     * NESTED FILES:
     *   Files found inside subdirectories (e.g. Q1/src/Q1a.java) are stored as
     *   relative paths ("src/Q1a.java") with a [NESTED] log. GradingPlanBuilder's
     *   resolveTemplateId() strips the path prefix before matching.
     *
     * [TOO-MANY-FILES]:
     *   If count > MAX_FILES_PER_QUESTION a warning is logged but we do not fail,
     *   since the instructor may legitimately have many helper files.
     */
    private List<String> collectFilesRecursively(Path qFolderRoot, String qFolderName)
            throws IOException {

        List<String> result = new ArrayList<>();

        try (Stream<Path> walk = Files.walk(qFolderRoot, MAX_NESTING_DEPTH)) {
            walk.filter(Files::isRegularFile).forEach(filePath -> {
                try {
                    Path relative = qFolderRoot.relativize(filePath);

                    // Skip files that sit inside a Q-named subfolder
                    if (relative.getNameCount() > 1) {
                        String firstSegment = relative.getName(0).toString();
                        if (firstSegment.matches("^Q[1-9]\\d*$")) {
                            System.out.println("         \u26A0\uFE0F  [SKIP-Q-FOLDER] " + relative
                                + ": Q-named subfolder inside " + qFolderName
                                + " — skipped (treat as a separate question folder).");
                            return;
                        }
                    }

                    String fileName = filePath.getFileName().toString();
                    String lower = fileName.toLowerCase();

                    if (!lower.endsWith(".java") && !lower.endsWith(".class")
                            && !lower.endsWith(".txt") && !lower.endsWith(".csv")) {
                        return; // ignore unrelated file types
                    }

                    if (relative.getNameCount() > 1) {
                        // Nested file: store relative path so resolveTemplateId() can strip it
                        String relativePath = relative.toString().replace("\\", "/");
                        System.out.println("         \u2139\uFE0F  [NESTED] " + relativePath
                            + ": found inside subdirectory of " + qFolderName);
                        result.add(relativePath);
                    } else {
                        result.add(fileName);
                    }

                } catch (Exception e) {
                    System.err.println("         \u26A0\uFE0F  [WARN] Could not process: "
                        + filePath + " — " + e.getMessage());
                }
            });
        }

        if (result.size() > MAX_FILES_PER_QUESTION) {
            System.out.println("      \u26A0\uFE0F  [TOO-MANY-FILES] " + qFolderName + " has "
                + result.size() + " files (cap: " + MAX_FILES_PER_QUESTION + "). "
                + "Check for accidental file dumps.");
        }

        return result;
    }

    /**
     * Recursively deletes a directory.
     * Logs [WARN] to stderr on individual failures rather than silently ignoring,
     * which previously caused temp-file leaks on Windows when antivirus locked files.
     */
    private void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) return;

        Files.walk(directory)
            .sorted(Comparator.reverseOrder())
            .forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    System.err.println("\u26A0\uFE0F  [WARN] Could not delete temp file: "
                        + path + " — " + e.getMessage());
                }
            });
    }

    // ================================================================
    // INNER CLASS: Natural question-number sort
    // ================================================================

    /**
     * Sorts Q folder names numerically: Q1, Q2, Q10 — not Q1, Q10, Q2.
     *
     * Uses substring(1) to strip the leading "Q" before parsing the integer.
     * NOT replaceAll("\\D+","") — that would turn "Version2Q10" into "210",
     * giving completely wrong sort order for any non-standard folder names.
     */
    private static class QuestionComparator implements Comparator<String> {

        @Override
        public int compare(String a, String b) {
            return Integer.compare(extractNumber(a), extractNumber(b));
        }

        private int extractNumber(String folderName) {
            try {
                return Integer.parseInt(folderName.substring(1));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }
}
