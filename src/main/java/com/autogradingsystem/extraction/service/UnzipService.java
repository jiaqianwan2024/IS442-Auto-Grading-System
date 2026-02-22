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
 * Flattens deep wrapper folders (e.g., username/wrapper1/wrapper2/Q1/)
 * * PURPOSE:
 * - Detects the "True Root" of student work, even if deeply nested
 * - Moves Q1/, Q2/ folders or Q1.java files up to the student root
 * - Deletes redundant empty wrapper folders (e.g., "final", "submission")
 * * WHY RECURSIVE?
 * - Students often submit ZIPs with multiple nested folders
 * - Standardizing the structure is critical for the Execution Layer (Phase 3)
 * * LOGGING:
 * - Logs the relative path being flattened for debugging
 * - Logs success upon structure normalization
 * * @param studentDir Path to student's root directory in resources/output/extracted/
 * @throws IOException if moving files or deleting wrappers fails
 */
private void flattenWrapperFolder(Path studentDir) throws IOException {
    if (!Files.exists(studentDir) || !Files.isDirectory(studentDir)) {
        return;
    }

    // 1. Locate the "True Root" containing Q folders or files
    Path trueRoot = findTrueRoot(studentDir);

    // 2. If trueRoot is deeper than studentDir, normalize the structure
    if (trueRoot != null && !trueRoot.equals(studentDir)) {
        System.out.println("   🔧 Deep nesting detected. Flattening from: " + studentDir.relativize(trueRoot));
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(trueRoot)) {
            for (Path item : stream) {
                Path target = studentDir.resolve(item.getFileName());
                // Moves items up to the student ID folder
                Files.move(item, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        // 3. Clean up the empty intermediate "wrapper" folders
        cleanupEmptyWrappers(studentDir);
        System.out.println("   ✅ Structure flattened successfully");
    }
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

    /**
 * findTrueRoot - Recursively locates the core project directory
 * * PURPOSE:
 * - Digs through nested folders until it finds the actual question files or folders
 * - Handles student errors like: submission.zip/final_version/john_doe/Q1/
 * * STRATEGY:
 * 1. Scans current directory for any item matching the "Q" pattern (Q1, Q1.java, etc.)
 * 2. If found, returns this path as the "True Root"
 * 3. If NOT found, checks if there is exactly one subfolder
 * 4. If exactly one folder exists, recurses into that folder to keep "digging"
 * * @param currentPath The directory to search within
 * @return Path to the directory containing Q-items, or null if structure is unrecognizable
 * @throws IOException if directory access fails
 */
private Path findTrueRoot(Path currentPath) throws IOException {
    // Check if current directory contains Q folders (Q1/) OR Q files (Q1.java)
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(currentPath)) {
        for (Path item : stream) {
            String name = item.getFileName().toString();
            // Regex matches "Q" followed by digits (e.g., Q1, Q1a, Q1.java)
            if (name.matches("^Q\\d+.*$")) {
                return currentPath; 
            }
        }
    }

    // Fallback: If no Q-items found, look deeper if there's exactly one subfolder
    List<Path> subfolders = new ArrayList<>();
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(currentPath)) {
        for (Path item : stream) {
            // Only count directories, ignoring hidden system files like .DS_Store
            if (Files.isDirectory(item) && !item.getFileName().toString().startsWith(".")) {
                subfolders.add(item);
            }
        }
    }

    // Only recurse if there's a single clear path to follow
    if (subfolders.size() == 1) {
        return findTrueRoot(subfolders.get(0));
    }

    return null;
}

/**
 * cleanupEmptyWrappers - Removes redundant folders after flattening
 * * PURPOSE:
 * - After moving Q1 up to the root, the old "wrapper" folders (like "final") are empty
 * - This method identifies and deletes those non-essential directories
 * * SAFETY:
 * - It specifically ignores folders named "Q1", "Q2", etc., to avoid deleting student work
 * - Uses the existing recursive deleteDirectory method for thorough cleanup
 * * @param studentDir The student's root folder in output/extracted/
 * @throws IOException if deletion fails
 */
private void cleanupEmptyWrappers(Path studentDir) throws IOException {
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(studentDir)) {
        for (Path item : stream) {
            String name = item.getFileName().toString();
            
            // If it's a directory but NOT a question folder, it's a wrapper to be deleted
            if (Files.isDirectory(item) && !name.matches("^Q\\d+$")) {
                // Uses your existing recursive delete helper
                deleteDirectory(item);
            }
        }
    }
}
}