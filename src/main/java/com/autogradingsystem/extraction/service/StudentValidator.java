package com.autogradingsystem.extraction.service;

import com.autogradingsystem.extraction.model.ValidationResult;
import com.autogradingsystem.extraction.model.ValidationResult.Status;

import java.io.IOException;
import java.nio.file.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * StudentValidator - 3-Layer Student Identification System
 * 
 * PURPOSE:
 * - Identifies students even when they make submission mistakes
 * - Tries 3 different detection methods in priority order
 * - Extracts student submissions to correct folder names
 * 
 * THE PROBLEM:
 * Students often make mistakes when submitting:
 * - Wrong ZIP filename: "my-submission.zip" instead of "2023-2024-ping.lee.2023.zip"
 * - Forgot to rename folder: Still named "RenameToYourUsername"
 * - Typos in filenames
 * 
 * THE SOLUTION: 3-LAYER DETECTION
 * Layer 1: Check ZIP filename (most reliable)
 * Layer 2: Check folder name inside ZIP (second most reliable)
 * Layer 3: Scan Java comments for @author tag (last resort)
 * 
 * CHANGES FROM v3.0:
 * - ValidationResult moved to separate model class
 * - No logging (handled by ExtractionController)
 * - Cleaner method signatures
 * 
 * @author IS442 Team
 * @version 4.0 (Spring Boot Microservices Structure)
 */
public class StudentValidator {
    
    /**
     * Validates student using 3-layer detection and extracts if valid
     * 
     * WORKFLOW:
     * 1. Try Layer 1: Extract username from ZIP filename
     * 2. If Layer 1 fails → Try Layer 2: Check folder name inside ZIP
     * 3. If Layer 2 fails → Try Layer 3: Scan Java comments
     * 4. If all layers fail → Mark as UNRECOGNIZED
     * 5. If identified → Extract to destination/{username}/
     * 
     * @param studentZip Path to student's submission ZIP
     * @param destination Base directory to extract students to
     * @param scoreReader ScoreSheetReader with valid students loaded
     * @return ValidationResult indicating how student was identified
     * @throws IOException if validation or extraction fails
     */
    public ValidationResult validate3Layer(
            Path studentZip, 
            Path destination, 
            ScoreSheetReader scoreReader) throws IOException {
        
        String zipFilename = studentZip.getFileName().toString();
        
        // ================================================================
        // LAYER 1: FILENAME MATCHING (Most Reliable)
        // ================================================================
        // Expected format: 2023-2024-ping.lee.2023.zip
        // Pattern: YYYY-YYYY-{username}.zip
        // Extract: {username}
        
        String usernameFromFilename = extractUsernameFromFilename(zipFilename);
        
        if (usernameFromFilename != null && scoreReader.isValid(usernameFromFilename)) {
            // Layer 1 success - extract and return
            Path extractPath = destination.resolve(usernameFromFilename);
            ZipFileProcessor.unzip(studentZip, extractPath);
            
            return new ValidationResult(
                zipFilename,
                Status.MATCHED,
                usernameFromFilename
            );
        }
        
        // ================================================================
        // LAYER 2: FOLDER NAME MATCHING (Second Most Reliable)
        // ================================================================
        // Look inside the ZIP for folder name
        // Expected: ping.lee.2023/ containing Q1/, Q2/, Q3/
        
        String usernameFromFolder = extractUsernameFromFolder(studentZip, scoreReader);
        
        if (usernameFromFolder != null) {
            // Layer 2 success - extract and return
            Path extractPath = destination.resolve(usernameFromFolder);
            ZipFileProcessor.unzip(studentZip, extractPath);
            
            return new ValidationResult(
                zipFilename,
                Status.RECOVERED_FOLDER,
                usernameFromFolder
            );
        }
        
        // ================================================================
        // LAYER 3: JAVA COMMENT SCANNING (Last Resort)
        // ================================================================
        // Scan .java files for @author comments
        // Expected: @author ping.lee.2023
        
        String usernameFromComment = extractUsernameFromComments(studentZip, scoreReader);
        
        if (usernameFromComment != null) {
            // Layer 3 success - extract and return
            Path extractPath = destination.resolve(usernameFromComment);
            ZipFileProcessor.unzip(studentZip, extractPath);
            
            return new ValidationResult(
                zipFilename,
                Status.RECOVERED_COMMENT,
                usernameFromComment
            );
        }
        
        // ================================================================
        // ALL LAYERS FAILED - UNRECOGNIZED
        // ================================================================
        
        return new ValidationResult(
            zipFilename,
            Status.UNRECOGNIZED,
            null
        );
    }
    
    /**
     * Layer 1: Extracts username from ZIP filename
     * 
     * PATTERN MATCHING:
     * Expected format: YYYY-YYYY-{username}.zip
     * Examples:
     *   2023-2024-ping.lee.2023.zip → ping.lee.2023 ✅
     *   2024-2025-chee.teo.2022.zip → chee.teo.2022 ✅
     *   my-submission.zip → null ❌
     * 
     * REGEX EXPLANATION:
     * \\d{4}-\\d{4}-  = "YYYY-YYYY-" (year range)
     * (.+?)            = capture group: any characters (username)
     * \\.zip$          = ends with ".zip"
     * 
     * @param filename ZIP filename
     * @return Username if pattern matches, null otherwise
     */
    private String extractUsernameFromFilename(String filename) {
        
        // Pattern: YYYY-YYYY-{username}.zip
        Pattern pattern = Pattern.compile("\\d{4}-\\d{4}-(.+?)\\.zip$");
        Matcher matcher = pattern.matcher(filename);
        
        if (matcher.find()) {
            return matcher.group(1);  // Extract captured group (username)
        }
        
        return null;
    }
    
    /**
     * Layer 2: Looks for username in folder name inside ZIP
     * 
     * STRATEGY:
     * 1. Extract ZIP to temporary location
     * 2. Look at top-level folders
     * 3. Check if any folder name is a valid username
     * 4. Cleanup temp files
     * 
     * WHY THIS WORKS:
     * - Students often correctly name the folder inside ZIP
     * - Even if they rename the ZIP file to something else
     * - Example: my-submission.zip contains ping.lee.2023/ folder
     * 
     * @param studentZip ZIP file to inspect
     * @param scoreReader ScoreSheetReader for validation
     * @return Username if found, null otherwise
     */
    private String extractUsernameFromFolder(Path studentZip, ScoreSheetReader scoreReader) 
            throws IOException {
        
        // Create temporary directory
        Path tempDir = Files.createTempDirectory("student-check");
        
        try {
            // Extract ZIP to temp location
            ZipFileProcessor.unzip(studentZip, tempDir);
            
            // Check top-level folders
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(tempDir)) {
                for (Path item : stream) {
                    if (Files.isDirectory(item)) {
                        String folderName = item.getFileName().toString();
                        
                        // Check if folder name is a valid username
                        if (scoreReader.isValid(folderName)) {
                            return folderName;
                        }
                    }
                }
            }
            
            return null;
            
        } finally {
            // Cleanup temporary directory
            deleteDirectory(tempDir);
        }
    }
    
    /**
     * Layer 3: Scans Java files for @author comments
     * 
     * STRATEGY:
     * 1. Extract ZIP to temporary location
     * 2. Find all .java files recursively
     * 3. Read each file line by line
     * 4. Look for @author comments
     * 5. Extract username from comment
     * 6. Validate against official list
     * 7. Cleanup temp files
     * 
     * COMMENT PATTERNS WE DETECT:
     * - @author ping.lee.2023
     * - @author: ping.lee.2023
     * - * @author ping.lee.2023
     * - // @author ping.lee.2023
     * 
     * REGEX EXPLANATION:
     * @author\\s*:?\\s*  = "@author" + optional whitespace + optional ":" + whitespace
     * (\\S+)             = capture group: non-whitespace characters (username)
     * 
     * @param studentZip ZIP file to scan
     * @param scoreReader ScoreSheetReader for validation
     * @return Username if found in comments, null otherwise
     */
    private String extractUsernameFromComments(Path studentZip, ScoreSheetReader scoreReader) 
            throws IOException {
        
        // Create temporary directory
        Path tempDir = Files.createTempDirectory("comment-scan");
        
        try {
            // Extract ZIP to temp location
            ZipFileProcessor.unzip(studentZip, tempDir);
            
            // Find all .java files
            Pattern authorPattern = Pattern.compile("@author\\s*:?\\s*(\\S+)");
            
            // Walk directory tree to find .java files
            try (var stream = Files.walk(tempDir)) {
                var javaFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .toArray(Path[]::new);
                
                // Scan each Java file
                for (Path javaFile : javaFiles) {
                    
                    // Read file line by line
                    for (String line : Files.readAllLines(javaFile)) {
                        
                        Matcher matcher = authorPattern.matcher(line);
                        
                        if (matcher.find()) {
                            String potentialUsername = matcher.group(1);
                            
                            // Validate against official list
                            if (scoreReader.isValid(potentialUsername)) {
                                return potentialUsername;
                            }
                        }
                    }
                }
            }
            
            return null;
            
        } finally {
            // Cleanup temporary directory
            deleteDirectory(tempDir);
        }
    }
    
    /**
     * Recursively deletes a directory and all its contents
     * Used for cleaning up temporary extraction directories
     * 
     * @param directory Directory to delete
     * @throws IOException if deletion fails
     */
    private void deleteDirectory(Path directory) throws IOException {
        
        if (!Files.exists(directory)) {
            return;
        }
        
        Files.walk(directory)
            .sorted(java.util.Comparator.reverseOrder())
            .forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    // Ignore - best effort cleanup
                }
            });
    }
}