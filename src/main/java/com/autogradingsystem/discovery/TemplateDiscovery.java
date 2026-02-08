package com.autogradingsystem.discovery;

import com.autogradingsystem.model.ExamStructure;
import com.autogradingsystem.service.file.ZipFileProcessor;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TemplateDiscovery - Automatic Exam Structure Detection
 * 
 * PURPOSE:
 * - Scans the template ZIP to discover exam structure automatically
 * - Finds all question folders (Q1/, Q2/, Q3/, etc.)
 * - Identifies subquestion files (Q1a.java, Q1b.java, etc.)
 * - Builds complete ExamStructure without any hardcoding
 * - Automatically handles nested ZIP structures (wrapper folders)
 * 
 * WHY WE NEED THIS:
 * - Eliminates hardcoded question lists in ExecutionController
 * - Adapts automatically to different exam structures
 * - Instructor can add/remove questions without code changes
 * 
 * CONVENTION USED:
 * - Top-level folders starting with "Q" are question folders (Q1/, Q2/, Q3/)
 * - Java files inside folders are subquestions (Q1a.java, Q1b.java)
 * - Ignores non-question folders (like __MACOSX, .git, etc.)
 * 
 * CROSS-PLATFORM NOTES:
 * - Uses java.nio.file.Path API (works on Windows, Mac, Linux)
 * - DirectoryStream for efficient directory iteration
 * - Handles different path separators automatically
 * 
 * SUPPORTED ZIP STRUCTURES:
 * 
 * Flat structure:
 * template.zip
 * ├── Q1/
 * ├── Q2/
 * └── Q3/
 * 
 * Nested structure (automatically detected):
 * template.zip
 * └── RenameToYourUsername/    ← Wrapper folder
 *     ├── Q1/
 *     ├── Q2/
 *     └── Q3/
 * 
 * @author IS442 Team
 * @version 1.1 (Added wrapper folder detection)
 */
public class TemplateDiscovery {
    
    /**
     * Discovers exam structure from template ZIP file.
     * 
     * WORKFLOW:
     * 1. Extract template ZIP to temporary directory
     * 2. Detect and handle wrapper folders (if present)
     * 3. Scan for question folders starting with "Q"
     * 4. For each question folder, find all .java files
     * 5. Build ExamStructure object
     * 6. Cleanup temporary directory
     * 7. Return structure
     * 
     * WRAPPER FOLDER DETECTION:
     * - If only one folder at top level, it's treated as a wrapper
     * - Automatically navigates inside to find question folders
     * - Common in ZIPs created on Windows/Mac
     * 
     * @param templateZipPath Path to template ZIP file
     * @return ExamStructure object with discovered questions and files
     * @throws IOException if ZIP cannot be read or extracted
     */
    public ExamStructure discoverStructure(Path templateZipPath) throws IOException {
        
        System.out.println("\n📂 Discovering exam structure from template...");
        System.out.println("   Template: " + templateZipPath.getFileName());
        
        // STEP 1: Create temporary directory for extraction
        Path tempDir = Files.createTempDirectory("template_extract");
        
        try {
            // STEP 2: Extract template ZIP to temp directory
            ZipFileProcessor.unzip(templateZipPath, tempDir);
            
            // STEP 3: Initialize data structures
            List<String> questions = new ArrayList<>();
            Map<String, List<String>> questionFiles = new HashMap<>();
            
            // STEP 4: Handle potential wrapper folder
            // Many ZIPs have structure like: template.zip/TemplateName/Q1/Q2/Q3
            // We need to detect this and navigate to the correct level
            
            Path scanDir = tempDir;
            
            // Check if there's a single wrapper folder
            try (DirectoryStream<Path> topLevel = Files.newDirectoryStream(tempDir)) {
                int folderCount = 0;
                int fileCount = 0;
                Path singleFolder = null;
                
                for (Path entry : topLevel) {
                    if (Files.isDirectory(entry)) {
                        folderCount++;
                        singleFolder = entry;
                    } else {
                        fileCount++;
                    }
                }
                
                // If only one folder at top level (and no files or few files), it's likely a wrapper
                // Navigate one level deeper
                if (folderCount == 1 && fileCount <= 2) {  // Allow up to 2 files (like .DS_Store, README)
                    scanDir = singleFolder;
                    System.out.println("   📂 Detected wrapper folder: " + singleFolder.getFileName());
                    System.out.println("   📂 Scanning inside wrapper for question folders...");
                }
            }
            
            // STEP 5: Scan for question folders (now in the correct directory)
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(scanDir)) {
                
                for (Path entry : stream) {
                    
                    // Only process directories (skip files)
                    if (Files.isDirectory(entry)) {
                        
                        String folderName = entry.getFileName().toString();
                        
                        // STEP 5a: Check if this is a question folder
                        if (isQuestionFolder(folderName)) {
                            
                            // Extract question ID (folder name)
                            String questionId = folderName;
                            questions.add(questionId);
                            
                            // STEP 5b: Find all Java files in this question folder
                            List<String> javaFiles = findJavaFiles(entry);
                            questionFiles.put(questionId, javaFiles);
                            
                            // Log discovered question
                            System.out.println("   ✅ " + questionId + " (" + javaFiles.size() + 
                                             " file" + (javaFiles.size() == 1 ? "" : "s") + ": " + 
                                             String.join(", ", javaFiles) + ")");
                        }
                    }
                }
            }
            
            // STEP 6: Sort questions alphabetically
            questions.sort(String::compareTo);
            
            // STEP 7: Create and return ExamStructure
            ExamStructure structure = new ExamStructure(questions, questionFiles);
            
            System.out.println("\n   📊 Total: " + questions.size() + " question(s), " + 
                             countTotalFiles(questionFiles) + " file(s)");
            
            return structure;
            
        } finally {
            // STEP 8: Cleanup - Always delete temp directory
            deleteDirectory(tempDir);
        }
    }
    
    /**
     * Checks if a folder name represents a question folder.
     * 
     * CONVENTION:
     * - Must start with "Q"
     * - Followed by a number (Q1, Q2, Q3, Q10, etc.)
     * - Case-sensitive: "Q1" ✅, "q1" ❌
     * 
     * @param folderName Name of the folder to check
     * @return true if folder is a question folder, false otherwise
     */
    private boolean isQuestionFolder(String folderName) {
        // Pattern: Starts with "Q" followed by one or more digits
        return folderName.matches("^Q\\d+$");
    }
    
    /**
     * Finds all Java files in a question folder.
     * 
     * @param questionFolder Path to question folder
     * @return List of Java filenames in alphabetical order
     * @throws IOException if folder cannot be read
     */
    private List<String> findJavaFiles(Path questionFolder) throws IOException {
        
        List<String> javaFiles = new ArrayList<>();
        
        // Iterate through all files in the question folder
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(questionFolder)) {
            
            for (Path file : stream) {
                
                // Only process files (skip subdirectories)
                if (Files.isRegularFile(file)) {
                    
                    String filename = file.getFileName().toString();
                    
                    // Check if it's a Java file
                    if (filename.endsWith(".java")) {
                        javaFiles.add(filename);
                    }
                }
            }
        }
        
        // Sort files alphabetically for consistent ordering
        javaFiles.sort(String::compareTo);
        
        return javaFiles;
    }
    
    /**
     * Counts total number of Java files across all questions.
     * 
     * @param questionFiles Map of question ID → list of files
     * @return Total file count
     */
    private int countTotalFiles(Map<String, List<String>> questionFiles) {
        return questionFiles.values().stream()
                .mapToInt(List::size)
                .sum();
    }
    
    /**
     * Recursively deletes a directory and all its contents.
     * 
     * @param dir Directory to delete recursively
     * @throws IOException if deletion fails
     */
    private void deleteDirectory(Path dir) throws IOException {
        
        if (Files.exists(dir)) {
            
            Files.walk(dir)
                // Sort paths in reverse order (deepest first)
                .sorted((a, b) -> -a.compareTo(b))
                // Delete each path
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        // Log error but continue cleanup
                        System.err.println("⚠️  Could not delete: " + path);
                    }
                });
        }
    }
}