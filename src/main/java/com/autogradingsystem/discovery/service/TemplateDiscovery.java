package com.autogradingsystem.discovery.service;

import com.autogradingsystem.discovery.model.ExamStructure;
import com.autogradingsystem.extraction.service.ZipFileProcessor;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

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

        // Create temporary directory for extraction
        Path tempDir = Files.createTempDirectory("template-discovery");
        
        try {
            // Extract template ZIP
            ZipFileProcessor.unzip(templateZip, tempDir);
            
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
            
            System.out.println("   ✅ Template Discovery Complete. Discovered " + questionFiles.size() + " question folder(s).");

            // Build and return ExamStructure
            return new ExamStructure(questionFiles);
            
        } finally {
            // Cleanup temporary directory
            deleteDirectory(tempDir);
        }
    }
    
    /**
     * Finds the root directory containing Q folders
     * Handles nested ZIP structures automatically
     * * LOGIC:
     * 1. If tempDir directly contains Q folders → return tempDir
     * 2. If tempDir has one subfolder containing Q folders → return subfolder
     * 3. Otherwise → throw exception (invalid structure)
     * * WHY NEEDED?
     * - Some LMS systems add extra wrapper folder when downloading
     * - Template.zip contains RenameToYourUsername/ which contains Q1/, Q2/
     * - We want to find where Q folders actually are
     * * @param tempDir Temporary extraction directory
     * @return Path to directory containing Q folders
     * @throws IOException if structure is invalid
     */
    private Path findRootDirectory(Path tempDir) throws IOException {
        
        // Check if tempDir directly has Q folders
        if (hasQuestionFolders(tempDir)) {
            return tempDir;
        }
        
        // Look one level deeper
        List<Path> subdirs = new ArrayList<>();
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(tempDir)) {
            for (Path item : stream) {
                if (Files.isDirectory(item)) {
                    subdirs.add(item);
                }
            }
        }
        
        // Check if there's exactly one subdirectory with Q folders
        if (subdirs.size() == 1 && hasQuestionFolders(subdirs.get(0))) {
            System.out.println("      📂 Deep nesting detected. Flattening from: " + subdirs.get(0).getFileName());
            return subdirs.get(0);
        }
        
        throw new IOException(
            "Invalid template structure!\n" +
            "Expected: Template ZIP should contain Q1/, Q2/, Q3/ folders\n" +
            "Or: Template ZIP → folder → Q1/, Q2/, Q3/"
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
                    
                    // Check if name matches Q pattern (Q1, Q2, Q3, etc.)
                    if (name.matches("(?i)(Q|Task|Part)?\\d+.*")) { 
                        return true; 
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Scans root directory for Q folders and lists .java files in each
     * * WORKFLOW:
     * 1. Find all Q folders (Q1, Q2, Q3, etc.)
     * 2. For each Q folder:
     * a. List all .java files
     * b. Sort files alphabetically
     * c. Store in map: Question → File list
     * 3. Sort questions numerically (Q1 before Q2 before Q10)
     * * SORTING LOGIC:
     * - Uses natural sort order: Q1, Q2, Q3, ..., Q9, Q10, Q11
     * - Not alphabetical: Q1, Q10, Q11, Q2 (wrong!)
     * - Extracts number from Qxx and sorts numerically
     * * @param rootDir Root directory containing Q folders
     * @return Map of question folder → list of .java files
     * @throws IOException if scanning fails
     */
    private Map<String, List<String>> scanForQuestions(Path rootDir) throws IOException {
        
        // Use TreeMap with custom comparator for natural sorting
        Map<String, List<String>> questionFiles = new TreeMap<>(new QuestionComparator());
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(rootDir)) {
            for (Path item : stream) {
                
                if (Files.isDirectory(item)) {
                    String folderName = item.getFileName().toString();
                    
                    // Check if this is a Q folder
                    if (folderName.matches("(?i)(Q|Task|Part)?\\d+.*")) {
                        
                        // List all .java files in this Q folder
                        List<String> javaFiles = new ArrayList<>();
                        
                        try (DirectoryStream<Path> fileStream = Files.newDirectoryStream(item, "{*.java,*.class,*.txt}")) {
                            for (Path file : fileStream) {
                                javaFiles.add(file.getFileName().toString());
                            }
                        }
                        
                        // Sort files alphabetically
                        Collections.sort(javaFiles);
                        
                        // Store in map
                        questionFiles.put(folderName, javaFiles);

                        // LOG: Discovery of folder and contents
                        System.out.println("      📁 Found Question Folder: [" + folderName + "] with " + javaFiles.size() + " file(s)");
                        for (String javaFile : javaFiles) {
                            System.out.println("         📄 Expected File: " + javaFile);
                        }
                    }
                }
            }
        }
        
        return questionFiles;
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
                    // Ignore - best effort cleanup
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
            try {
                // Use Regex to pull only the digits out of the folder name
                // This handles "Q1", "Task10", or even "1" correctly
                String digits = questionFolder.replaceAll("\\D+", ""); 
                return digits.isEmpty() ? 0 : Integer.parseInt(digits);
            } catch (NumberFormatException e) {
                return 0; // Safe fallback so the program never crashes
            }
        }
    }
}