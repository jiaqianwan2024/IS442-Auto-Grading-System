package com.autogradingsystem.extraction.service;

import com.autogradingsystem.PathConfig;
import com.autogradingsystem.extraction.model.ValidationResult;

import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.io.IOException;
import java.util.*;

/**
 * UnzipService - Extraction Orchestrator
 * 
 * PURPOSE:
 * - Coordinates student submission extraction workflow
 * - Integrates: ZIP extraction + Student validation + Wrapper flattening
 * - Called by ExtractionController
 * 
 * CHANGES FROM v3.0:
 * - Removed verbose logging (now handled by Main.java)
 * - Updated to use PathConfig for paths
 * - Accepts paths as parameters (no hardcoded paths)
 * - No auto-cleanup (handled by ExtractionController)
 * 
 * @author IS442 Team
 * @version 4.0 (Spring Boot Microservices Structure)
 */
public class UnzipService {
    
    private final StudentValidator validator;
    
    /**
     * Constructor - initializes validator
     */
    public UnzipService() {
        this.validator = new StudentValidator();
    }
    
    /**
     * Extracts and validates all student submissions
     * 
     * WORKFLOW:
     * 1. Find master submission ZIP
     * 2. Extract master ZIP to temp location
     * 3. Find all student ZIPs inside
     * 4. For each student ZIP: Validate and extract
     * 5. Flatten wrapper folders
     * 6. Cleanup temp files
     * 
     * LOGGING:
     * - Minimal logging (only wrapper flattening if detected)
     * - Main summary logged by ExtractionController/Main.java
     * 
     * @param submissionsDir Directory containing master ZIP
     * @param extractedDir Directory to extract students to
     * @param scoreReader ScoreSheetReader with valid students loaded
     * @return List of ValidationResult objects
     * @throws IOException if extraction fails
     */
    public List<ValidationResult> extractAndValidateStudents(
            Path submissionsDir,
            Path extractedDir,
            ScoreSheetReader scoreReader) throws IOException {
        
        // Find master submission ZIP
        Path masterZip = findNewestZip(submissionsDir);
        
        // Extract master ZIP to temp location
        Path tempExtract = Files.createTempDirectory("master-extract");
        
        try {
            ZipFileProcessor.unzip(masterZip, tempExtract);
            
            // Find student ZIPs inside
            List<Path> studentZips = findStudentZips(tempExtract);
            
            if (studentZips.isEmpty()) {
                throw new IOException(
                    "No student ZIPs found inside master ZIP!\n" +
                    "Expected structure: master ZIP contains individual student ZIPs"
                );
            }
            
            // Validate and extract each student
            List<ValidationResult> results = new ArrayList<>();
            
            for (Path studentZip : studentZips) {
                
                // Run 3-layer validation
                ValidationResult result = validator.validate3Layer(
                    studentZip,
                    extractedDir,
                    scoreReader
                );
                
                results.add(result);
                
                // Flatten wrapper folders if student was identified
                if (result.isIdentified()) {
                    try {
                        Path extractedPath = extractedDir.resolve(result.getResolvedId());
                        flattenWrapperFolder(extractedPath);
                    } catch (IOException e) {
                        System.err.println("⚠️  Warning: Could not flatten wrapper folder: " + 
                                         e.getMessage());
                    }
                }
            }
            
            return results;
            
        } finally {
            // Cleanup temporary directory
            deleteDirectory(tempExtract);
        }
    }
    
    /**
     * Flattens wrapper folders (e.g., 01400003/Q1/, ping.lee.2023/Q1/)
     * 
     * DETECTS:
     * - Single folder at top level containing Q folders
     * - Moves Q1/, Q2/, Q3/ up one level
     * - Deletes empty wrapper folder
     * 
     * HANDLES:
     * - Student ID wrappers (01400003/)
     * - Username wrappers (ping.lee.2023/)
     * - Template name wrappers (RenameToYourStudentID/)
     * 
     * LOGGING:
     * - Only logs if wrapper is detected and flattened
     * - This is important debugging information
     * 
     * @param studentDir Path to student's root directory
     * @throws IOException if flattening fails
     */
    private void flattenWrapperFolder(Path studentDir) throws IOException {
        
        if (!Files.exists(studentDir) || !Files.isDirectory(studentDir)) {
            return;
        }
        
        // List top-level items
        List<Path> folders = new ArrayList<>();
        List<Path> files = new ArrayList<>();
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(studentDir)) {
            for (Path item : stream) {
                if (Files.isDirectory(item)) {
                    folders.add(item);
                } else {
                    // Count files but ignore .DS_Store and other junk
                    String fileName = item.getFileName().toString();
                    if (!fileName.startsWith(".")) {
                        files.add(item);
                    }
                }
            }
        }
        
        // Detect wrapper: exactly 1 folder and few/no real files
        if (folders.size() != 1 || files.size() > 2) {
            return;  // Not a wrapper structure
        }
        
        Path wrapperFolder = folders.get(0);
        
        // Check if wrapper contains Q folders
        boolean hasQuestionFolders = false;
        List<Path> itemsToMove = new ArrayList<>();
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(wrapperFolder)) {
            for (Path item : stream) {
                itemsToMove.add(item);
                
                if (Files.isDirectory(item)) {
                    String name = item.getFileName().toString();
                    // Match Q1, Q2, Q3, Q10, etc.
                    if (name.matches("^Q\\d+$")) {
                        hasQuestionFolders = true;
                    }
                }
            }
        }
        
        // Only flatten if wrapper contains question folders
        if (!hasQuestionFolders) {
            return;
        }
        
        // Log wrapper flattening (important for debugging)
        System.out.println("   🔧 Flattening wrapper folder: " + wrapperFolder.getFileName());
        
        // Flatten: Move all items from wrapper to parent
        for (Path item : itemsToMove) {
            Path target = studentDir.resolve(item.getFileName());
            
            // If target already exists, delete it first
            if (Files.exists(target)) {
                if (Files.isDirectory(target)) {
                    deleteDirectory(target);
                } else {
                    Files.delete(target);
                }
            }
            
            Files.move(item, target, StandardCopyOption.REPLACE_EXISTING);
        }
        
        // Delete empty wrapper folder
        Files.delete(wrapperFolder);
        System.out.println("   ✅ Structure flattened successfully");
    }
    
    /**
     * Finds the newest ZIP file in a directory
     * 
     * @param directory Directory to search for ZIPs
     * @return Path to the newest ZIP file
     * @throws IOException if directory doesn't exist or no ZIPs found
     */
    private Path findNewestZip(Path directory) throws IOException {
        
        if (!Files.exists(directory)) {
            throw new IOException(
                "Directory not found: " + directory + "\n" +
                "Please create the directory and place student submissions there"
            );
        }
        
        List<Path> zipFiles = new ArrayList<>();
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.zip")) {
            for (Path zip : stream) {
                zipFiles.add(zip);
            }
        }
        
        if (zipFiles.isEmpty()) {
            throw new IOException(
                "No ZIP files found in: " + directory + "\n" +
                "Please place student-submission.zip in this folder"
            );
        }
        
        if (zipFiles.size() == 1) {
            return zipFiles.get(0);
        }
        
        // Multiple ZIPs found - find newest by modification time
        Path newest = zipFiles.get(0);
        FileTime newestTime = Files.getLastModifiedTime(newest);
        
        for (int i = 1; i < zipFiles.size(); i++) {
            Path current = zipFiles.get(i);
            FileTime currentTime = Files.getLastModifiedTime(current);
            
            if (currentTime.compareTo(newestTime) > 0) {
                newest = current;
                newestTime = currentTime;
            }
        }
        
        return newest;
    }
    
    /**
     * Finds all student ZIP files in the extracted master ZIP
     * 
     * HANDLES TWO COMMON STRUCTURES:
     * Structure A (with subfolder): master.zip/subfolder/*.zip
     * Structure B (flat): master.zip/*.zip
     * 
     * @param tempExtract Path to extracted master ZIP contents
     * @return List of student ZIP files
     * @throws IOException if directory cannot be read
     */
    private List<Path> findStudentZips(Path tempExtract) throws IOException {
        
        List<Path> studentZips = new ArrayList<>();
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(tempExtract)) {
            for (Path item : stream) {
                if (Files.isDirectory(item)) {
                    // Check subdirectory for ZIPs
                    try (DirectoryStream<Path> subStream = Files.newDirectoryStream(item, "*.zip")) {
                        for (Path zip : subStream) {
                            studentZips.add(zip);
                        }
                    }
                } else if (item.toString().endsWith(".zip")) {
                    // Direct ZIP file
                    studentZips.add(item);
                }
            }
        }
        
        return studentZips;
    }
    
    /**
     * Recursively deletes a directory and all its contents
     * 
     * @param directory Directory to delete
     * @throws IOException if deletion fails
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
                    // Ignore
                }
            });
    }
}