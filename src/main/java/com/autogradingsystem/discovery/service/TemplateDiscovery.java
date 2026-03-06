package com.autogradingsystem.discovery.service;

import com.autogradingsystem.discovery.model.ExamStructure;
import com.autogradingsystem.extraction.service.ZipFileProcessor;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * TemplateDiscovery - Discovers Exam Structure from Template ZIP
 * * PURPOSE:
 * - Automatically discovers exam structure (questions and files)
 * - No hardcoded question lists needed
 * - Scans template ZIP and extracts structure information
 * * CONVENTION OVER CONFIGURATION:
 * - Looks for folders matching Q1, Q2, Q3, Q10, etc.
 * - Lists all .java files in each question folder
 * - Identifies helper files (files without matching testers)
 * * WHY THIS MATTERS:
 * - Add Q4 to exam? Just add Q4/ folder in template - no code changes!
 * - Rename files? System adapts automatically
 * - Flexible and maintainable
 * * CHANGES FROM v3.0:
 * - Removed verbose logging (handled by DiscoveryController/Main.java)
 * - Updated to use PathConfig via controller
 * - Cleaner method signatures
 * * @author IS442 Team
 * @version 4.0 (Spring Boot Microservices Structure)
 */
public class TemplateDiscovery {
    
    /**
     * Discovers exam structure from template ZIP
     * * WORKFLOW:
     * 1. Extract template ZIP to temporary location
     * 2. Handle nested ZIP structures (common in downloads)
     * 3. Scan for Q folders (Q1, Q2, Q3, etc.)
     * 4. List all .java files in each Q folder
     * 5. Build ExamStructure object
     * 6. Cleanup temporary files
     * * HANDLES TWO COMMON STRUCTURES:
     * * Structure A (Flat):
     * RenameToYourUsername.zip
     * ├── Q1/
     * │   ├── Q1a.java
     * │   └── Q1b.java
     * ├── Q2/
     * └── Q3/
     * * Structure B (Nested - common from Canvas/Blackboard):
     * RenameToYourUsername.zip
     * └── RenameToYourUsername/  ← Extra wrapper
     * ├── Q1/
     * ├── Q2/
     * └── Q3/
     * * @param templateZip Path to template ZIP file
     * @return ExamStructure containing all discovered questions and files
     * @throws IOException if template cannot be read or is invalid
     */
    public ExamStructure discoverStructure(Path templateZip) throws IOException {
        
        System.out.println("   🔍 Scanning Template ZIP: " + templateZip.getFileName());

        // [Fix-16] Reject suspiciously large ZIPs before extraction.
        // A legitimate template is never more than a few MB. A 50MB+ file is
        // a ZIP bomb candidate or simply the wrong file. Avoids filling disk.
        final long MAX_ZIP_BYTES = 50L * 1024 * 1024; // 50 MB
        long zipSize = Files.size(templateZip);
        if (zipSize > MAX_ZIP_BYTES) {
            throw new IOException(
                "Template ZIP is too large to be a valid exam template: "
                + String.format("%.1f MB", zipSize / (1024.0 * 1024.0)) + "\n"
                + "Maximum allowed size is 50 MB. Please verify this is the correct file."
            );
        }

        // Create temporary directory for extraction
        Path tempDir = Files.createTempDirectory("template-discovery");
        
        try {
            // Extract template ZIP
            // NEW-A fix: catch ZipException separately to give a friendly message when
            // the ZIP is password-protected or corrupted, instead of showing a raw stack trace.
            try {
                ZipFileProcessor.unzip(templateZip, tempDir);
            } catch (java.util.zip.ZipException e) {
                throw new IOException(
                    "Failed to read template ZIP: " + templateZip.getFileName() + "\n" +
                    "Possible causes:\n" +
                    "  - ZIP file is password protected (please remove the password)\n" +
                    "  - ZIP file is corrupted or incomplete (please re-export)\n" +
                    "  - File is not a valid ZIP (e.g. renamed .docx or .pdf)\n" +
                    "Original error: " + e.getMessage()
                );
            }
            
            // Handle nested structure (find the actual root)
            Path rootDir = findRootDirectory(tempDir);
            
            // Scan for Q folders and their files
            Map<String, List<String>> questionFiles = scanForQuestions(rootDir);
            
            if (questionFiles.isEmpty()) {
                throw new IOException(
                    "No question folders found in template!\n" +
                    "Expected folders like Q1/, Q2/, Q3/ containing .java files"
                );
            }

            // TD-2 fix: Q folders were found but may all be empty (e.g. instructor
            // zipped the template before adding any .java files). isEmpty() only checks
            // if the map has keys - it doesn't check if the file lists are empty.
            // ExamStructure.isValid() checks that at least one Q folder has files.
            ExamStructure examStructure = new ExamStructure(questionFiles);
            if (!examStructure.isValid()) {
                throw new IOException(
                    "Template Q folders exist but contain no .java files!\n" +
                    "Found folders: " + questionFiles.keySet() + "\n" +
                    "Please ensure each Q folder contains its .java source files."
                );
            }
            
            System.out.println("   ✅ Template Discovery Complete. Discovered " + questionFiles.size() + " question folder(s).");

            return examStructure;
            
        } finally {
            // Cleanup temporary directory
            deleteDirectory(tempDir);
        }
    }
    
    /**
     * Finds the root directory containing Q folders.
     * Handles arbitrarily deep nesting automatically (Fix 2 — v2.5).
     *
     * LOGIC:
     * 1. If currentDir directly contains Q folders → return currentDir
     * 2. Collect real subdirectories (skip __MACOSX, hidden folders — TD-3 fix)
     * 3. If exactly one real subdir → recurse into it
     * 4. If zero or 2+ real subdirs with no Q folders → throw with clear message
     *
     * WHY RECURSIVE?
     * - v2.4 only looked one level deep: tempDir → one subfolder
     * - Some LMS exports or manual zips produce deeper nesting:
     *     template.zip/exported/final/RenameToYourUsername/Q1/
     * - This method now follows the single-subfolder chain at any depth,
     *   matching the same logic already used by UnzipService.findTrueRoot()
     *   on the student submission side.
     *
     * @param currentDir Directory to search from (called initially with tempDir)
     * @return Path to the deepest directory that directly contains Q folders
     * @throws IOException if structure cannot be resolved (0 Q folders anywhere,
     *                     or ambiguous fork with 2+ subdirs and no Q folders)
     */
    // [Fix-4] Maximum wrapper nesting depth before we give up.
    // Prevents StackOverflowError from a malicious or accidental ZIP with thousands
    // of nested empty folders. 20 levels is far more than any real LMS produces.
    private static final int MAX_NESTING_DEPTH = 20;

    private Path findRootDirectory(Path currentDir) throws IOException {
        return findRootDirectory(currentDir, 0);
    }

    private Path findRootDirectory(Path currentDir, int depth) throws IOException {
        if (depth > MAX_NESTING_DEPTH) {
            throw new IOException(
                "Template ZIP has too many nested wrapper folders (>" + MAX_NESTING_DEPTH + " levels).\n"
                + "This is likely a ZIP bomb or an incorrectly structured archive.\n"
                + "Please ensure the template has at most a few wrapper folders before Q1/, Q2/ etc."
            );
        }

        // Base case: this directory directly contains Q folders
        if (hasQuestionFolders(currentDir)) {
            return currentDir;
        }

        // Collect real subdirectories — skip __MACOSX and hidden OS folders (TD-3 fix).
        // Mac ZIPs place __MACOSX alongside the real wrapper folder; without this skip,
        // findRootDirectory() sees 2 subdirs and wrongly throws even on valid ZIPs.
        List<Path> subdirs = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(currentDir)) {
            for (Path item : stream) {
                if (Files.isDirectory(item)) {
                    String dirName = item.getFileName().toString();
                    if (dirName.equals("__MACOSX") || dirName.startsWith(".")) {
                        System.out.println("      🙈 Skipping OS folder: " + dirName);
                        continue;
                    }
                    subdirs.add(item);
                }
            }
        }

        // Recursive case: exactly one real subdir — follow the chain
        if (subdirs.size() == 1) {
            System.out.println("      📂 Wrapper folder detected, going deeper: "
                + subdirs.get(0).getFileName());
            return findRootDirectory(subdirs.get(0), depth + 1);  // recurse with depth counter [Fix-4]
        }

        // T-08 fix: check if MULTIPLE subdirs each have Q folders — ambiguous fork.
        // e.g. ZIP has both sectionA/Q1/ and sectionB/Q1/ — we cannot guess which is correct.
        // Give a specific message that names the conflicting branches so the instructor
        // knows exactly what to fix, rather than a generic "invalid structure" error.
        List<Path> branchesWithQ = subdirs.stream()
            .filter(d -> { try { return hasQuestionFolders(d); } catch (IOException e) { return false; } })
            .collect(Collectors.toList());

        if (branchesWithQ.size() > 1) {
            throw new IOException(
                "Ambiguous template structure — multiple folders each contain Q folders!\n" +
                "Cannot determine which branch is the real exam root.\n" +
                "Conflicting branches found in '" + currentDir.getFileName() + "':\n" +
                branchesWithQ.stream()
                    .map(p -> "  - " + p.getFileName())
                    .collect(Collectors.joining("\n")) + "\n" +
                "Please restructure so only one folder branch leads to Q1/, Q2/, Q3/."
            );
        }

        // Dead end: 0 subdirs means no Q folders anywhere in this branch;
        // 2+ subdirs with none having Q folders means ambiguous with no solution.
        throw new IOException(
            "Invalid template structure!\n" +
            "Expected: Template ZIP should contain Q1/, Q2/, Q3/ folders\n" +
            "  (at any depth, as long as each level has exactly one subfolder)\n" +
            "Found " + subdirs.size() + " subfolder(s) with no Q folders at: '"
                + currentDir.getFileName() + "'\n" +
            "Subfolders found: " + subdirs.stream()
                .map(p -> p.getFileName().toString())
                .collect(Collectors.joining(", "))
        );
    }
    
    /**
     * Checks if a directory contains Q folders
     * * Q FOLDER PATTERN:
     * - Must match: Q1, Q2, Q3, Q4, ..., Q10, Q11, etc.
     * - Case sensitive: Q1 ✅, q1 ❌, Question1 ❌
     * - Regex: ^Q\d+$
     * * @param dir Directory to check
     * @return true if directory contains at least one Q folder, false otherwise
     */
    private boolean hasQuestionFolders(Path dir) throws IOException {
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path item : stream) {
                if (Files.isDirectory(item)) {
                    String name = item.getFileName().toString();
                    
                    // Strict match: Q + non-zero digit + any digits (Q1, Q2, Q10)
                    // ^Q[1-9]\d*$ also rejects Q01, Q00 (leading zeros) which would
                    // collide with Q1 in QuestionComparator (both strip to digit 1)
                    if (name.matches("^Q[1-9]\\d*$")) { 
                        return true; 
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Scans root directory for Q folders and lists relevant files in each.
     *
     * WORKFLOW:
     * 1. Find all Q folders (Q1, Q2, Q3, etc.) directly under rootDir
     * 2. For each Q folder: recursively collect all .java, .class, .txt files
     *    at any depth inside the folder (Fix 1 — v2.5)
     * 3. Sort files alphabetically so .java comes before .class for same name
     * 4. Sort questions numerically (Q1 before Q2 before Q10)
     *
     * WHY RECURSIVE FILE COLLECTION?
     * - v2.4 used a flat DirectoryStream — only files directly in Q1/ were seen.
     * - Some submissions organise files in subfolders:
     *     Q1/src/Q1a.java  or  Q1/solution/Q1a.java
     * - These were completely invisible before; now they are collected and logged.
     * - A [NESTED] warning is printed so the instructor knows the file came from
     *   a subfolder (useful for spotting non-standard structure).
     *
     * @param rootDir Root directory containing Q folders
     * @return Map of question folder to sorted list of files found (any depth)
     * @throws IOException if scanning fails
     */
    private Map<String, List<String>> scanForQuestions(Path rootDir) throws IOException {

        // TreeMap with custom comparator for natural Q1, Q2, Q10 ordering
        Map<String, List<String>> questionFiles = new TreeMap<>(new QuestionComparator());

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(rootDir)) {
            for (Path item : stream) {

                if (Files.isDirectory(item)) {
                    String folderName = item.getFileName().toString();

                    // Strict match: Q + non-zero digit + more digits (Q1, Q2, Q10)
                    // ^Q[1-9]\d*$ rejects Q01, Q00 (leading zeros collide in QuestionComparator)
                    if (folderName.matches("^Q[1-9]\\d*$")) {

                        // Recursively collect all relevant files inside this Q folder
                        List<String> files = collectFilesRecursively(item, item);

                        // Sort alphabetically: .java before .class for same base name
                        Collections.sort(files);

                        questionFiles.put(folderName, files);

                        System.out.println("      📁 Found Question Folder: [" + folderName + "] with " + files.size() + " file(s)");
                        for (String f : files) {
                            System.out.println("         📄 Expected File: " + f);
                        }
                    }
                }
            }
        }

        return questionFiles;
    }

    /**
     * Recursively collects .java, .class, and .txt files from a Q folder,
     * including files in any subfolders. (Fix 1 — v2.5)
     *
     * FILE NAMING IN OUTPUT:
     * - Files directly in the Q folder → stored as plain filename ("Q1a.java")
     * - Files in subfolders → stored as relative path ("src/Q1a.java") and a
     *   [NESTED] warning is logged so the instructor can see the unusual structure.
     *
     * SKIPS:
     * - .DS_Store and __MACOSX entries (macOS junk)
     * - Hidden files (names starting with '.')
     *
     * @param qFolderRoot The top-level Q folder (used to compute relative paths)
     * @param currentDir  Directory currently being scanned (equals qFolderRoot on first call)
     * @return List of file path strings relative to qFolderRoot
     * @throws IOException if directory reading fails
     */
    // [Fix-17] Maximum files to collect per Q folder. A real exam question never
    // has hundreds of .java files — hitting this limit signals a malformed template.
    private static final int MAX_FILES_PER_QUESTION = 50;

    /**
     * Collects .java, .class, and .txt files from a Q folder at any depth.
     * [Fix-5] Replaced manual recursion with Files.walk(maxDepth) to eliminate
     * the StackOverflowError risk from deeply nested Q subfolders.
     * [Fix-17] Stops collecting after MAX_FILES_PER_QUESTION files and warns.
     *
     * FILE NAMING IN OUTPUT:
     * - Files directly in Q folder  → plain filename  ("Q1a.java")
     * - Files in subfolders         → relative path   ("src/Q1a.java") + [NESTED] warning
     *
     * SKIPS: .DS_Store, __MACOSX, hidden files, Q-named subfolders (T-36), nested ZIPs (T-13)
     *
     * @param qFolderRoot Top-level Q folder (used to compute relative paths and detect nesting)
     * @param ignored     Previously the currentDir for manual recursion — kept for signature
     *                    compatibility but unused; Files.walk handles traversal internally.
     * @return List of file path strings (plain names or relative paths from qFolderRoot)
     * @throws IOException if directory reading fails
     */
    private List<String> collectFilesRecursively(Path qFolderRoot, Path ignored) throws IOException {
        List<String> collected = new ArrayList<>();

        // Walk up to MAX_NESTING_DEPTH levels inside the Q folder.
        // Files.walk visits directories depth-first; we filter for regular files only.
        Files.walk(qFolderRoot, MAX_NESTING_DEPTH)
            .filter(Files::isRegularFile)
            .forEach(entry -> {
                String name = entry.getFileName().toString();

                // Skip OS junk and hidden files
                if (name.equals(".DS_Store") || name.contains("__MACOSX") || name.startsWith(".")) {
                    return;
                }

                // T-13 fix: warn on nested ZIPs
                if (name.toLowerCase().endsWith(".zip")) {
                    System.out.println("         ⚠️  [ZIP-IN-ZIP] Nested ZIP skipped: "
                        + qFolderRoot.relativize(entry).toString().replace("\\", "/")
                        + " — extract its contents manually.");
                    return;
                }

                // T-36 fix: skip files whose immediate parent is a Q-named folder
                // (other than qFolderRoot itself) — those belong to a sibling question.
                Path parent = entry.getParent();
                if (!parent.equals(qFolderRoot)) {
                    String parentName = parent.getFileName().toString();
                    if (parentName.matches("^Q[1-9]\\d*$")) {
                        System.out.println("         ⚠️  [SKIP-Q-FOLDER] Skipping file in Q-named subfolder '"
                            + parentName + "': " + name);
                        return;
                    }
                }

                String lower = name.toLowerCase();
                if (!lower.endsWith(".java") && !lower.endsWith(".class") && !lower.endsWith(".txt")) {
                    return;
                }

                // [Fix-17] Cap files per Q folder
                if (collected.size() >= MAX_FILES_PER_QUESTION) {
                    if (collected.size() == MAX_FILES_PER_QUESTION) {
                        System.out.println("         ⚠️  [TOO-MANY-FILES] " + qFolderRoot.getFileName()
                            + " has more than " + MAX_FILES_PER_QUESTION
                            + " files — stopping collection. Please review the template.");
                    }
                    return;
                }

                String relativePath = qFolderRoot.relativize(entry).toString().replace("\\", "/");
                boolean isNested = !entry.getParent().equals(qFolderRoot);
                if (isNested) {
                    System.out.println("         ⚠️  [NESTED] File in subfolder: " + relativePath
                        + " (expected directly in Q folder — will still be processed)");
                    collected.add(relativePath);
                } else {
                    collected.add(name);
                }
            });

        return collected;
    }
    /**
     * Recursively deletes a directory and all its contents
     * * @param directory Directory to delete
     */
    private void deleteDirectory(Path directory) throws IOException {
        
        if (!Files.exists(directory)) {
            return;
        }
        
        Files.walk(directory)
            .sorted(Comparator.reverseOrder())
            .forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    // [Fix-6] Log instead of silently swallowing — on Windows a locked file
                    // causes silent temp directory accumulation that is hard to diagnose.
                    System.err.println("   [WARN] Could not delete temp file (will be cleaned by OS on reboot): "
                        + path.getFileName() + " — " + e.getMessage());
                }
            });
    }
    
    /**
     * QuestionComparator - Natural sorting for question folders
     * * PROBLEM:
     * - Default alphabetical sort: Q1, Q10, Q11, Q2, Q3 (wrong!)
     * - We want: Q1, Q2, Q3, Q10, Q11 (correct!)
     * * SOLUTION:
     * - Extract number from Qxx
     * - Compare numbers numerically, not alphabetically
     * * EXAMPLE:
     * - Extract: Q10 → 10, Q2 → 2
     * - Compare: 2 < 10
     * - Result: Q2 comes before Q10 ✅
     */
    private static class QuestionComparator implements Comparator<String> {
        
        @Override
        public int compare(String q1, String q2) {
            
            // Extract numbers from Q1, Q2, etc.
            int num1 = extractQuestionNumber(q1);
            int num2 = extractQuestionNumber(q2);
            
            // Compare numerically
            return Integer.compare(num1, num2);
        }
        
        /**
         * Extracts question number from folder name
         * * @param questionFolder Folder name (e.g., "Q10")
         * @return Question number (e.g., 10)
         */
        private int extractQuestionNumber(String questionFolder) {
            // [Fix-10] substring(1) is correct, fast, and unambiguous for "Q10" -> 10.
            // Old replaceAll("\\D+","") stripped ALL non-digits — "Version2Q10" -> "210".
            // The regex gate ^Q[1-9]\d*$ ensures only valid "Qn" names reach here.
            try {
                return Integer.parseInt(questionFolder.substring(1));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }
}