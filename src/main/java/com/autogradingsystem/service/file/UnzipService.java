package com.autogradingsystem.service.file;

import com.autogradingsystem.service.validation.ScoreSheetReader;
import com.autogradingsystem.service.validation.StudentValidator;
import com.autogradingsystem.service.validation.StudentValidator.ValidationResult;

import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.io.IOException;
import java.util.*;

/**
 * UnzipService - High-Level Extraction Orchestrator
 * 
 * PURPOSE:
 * - Coordinates the complete student submission extraction workflow
 * - Integrates: ZIP extraction + Student validation + Error reporting
 * - Provides single entry point for the entire extraction process
 * 
 * WORKFLOW OVERVIEW:
 * 1. Load official student list from LMS CSV
 * 2. Find master submission ZIP (newest if multiple exist)
 * 3. Extract master ZIP to temp location
 * 4. Find all student ZIPs inside
 * 5. For each student ZIP: Validate and extract using 3-layer detection
 * 6. Generate validation summary report
 * 7. Cleanup temporary files
 * 
 * FOLLOWS PURE CONVENTION:
 * - Expects files in: data/input/submissions/
 * - Expects CSV in: config/IS442-ScoreSheet.csv
 * - Outputs to: data/extracted/
 * - No configuration files needed!
 * 
 * CROSS-PLATFORM DESIGN:
 * - All paths use Path API (not String concatenation)
 * - Uses Files.createTempDirectory() (works on Windows/Mac/Linux)
 * - DirectoryStream for directory iteration (cross-platform)
 * - Files.move() instead of File.renameTo() (more reliable)
 * 
 * @author IS442 Team
 * @version 2.0 (Pure Convention + Path API)
 */
public class UnzipService {
    
    // Service dependencies
    private final ScoreSheetReader scoreReader;
    private final StudentValidator validator;
    
    /**
     * Constructor - Initializes validation services
     * 
     * DESIGN PATTERN: Dependency Injection (simple form)
     * - Creates dependencies in constructor
     * - Alternative: Could accept them as parameters (better for testing)
     */
    public UnzipService() {
        this.scoreReader = new ScoreSheetReader();
        this.validator = new StudentValidator();
    }
    
    /**
     * Complete extraction and validation workflow.
     * This is the main entry point - call this to extract everything!
     * 
     * RETURNS:
     * List of ValidationResult objects - one per student submission
     * Caller can inspect results to see which students were recovered vs unrecognized
     * 
     * EXCEPTIONS:
     * Throws IOException if critical files missing or filesystem errors
     * - Missing CSV: Instructor forgot to place scoresheet
     * - Missing ZIP: Instructor forgot to place submissions
     * - Permission denied: Filesystem doesn't allow file operations
     * 
     * @return List of validation results (one per student)
     * @throws IOException if critical setup fails
     */
    public List<ValidationResult> extractAndValidateStudents() throws IOException {
        
        // ================================================================
        // HEADER: Print start banner
        // ================================================================
        System.out.println("\n" + "=".repeat(70));
        System.out.println("🚀 EXTRACTION & VALIDATION - Phase 1");
        System.out.println("=".repeat(70));
        
        // ================================================================
        // STEP 1: Load official student list from LMS CSV
        // ================================================================
        // Path is relative to project root (cross-platform)
        // Windows: config\IS442-ScoreSheet.csv
        // Mac: config/IS442-ScoreSheet.csv
        // Path.of() handles both!
        Path csvPath = Paths.get("config", "IS442-ScoreSheet.csv");
        
        // VALIDATION: Check if CSV exists
        if (!Files.exists(csvPath)) {
            throw new IOException(
                "❌ LMS scoresheet not found: " + csvPath + "\n" +
                "   Please place IS442-ScoreSheet.csv in the config/ folder"
            );
        }
        
        System.out.println("\n📋 Step 1: Loading official student list...");
        scoreReader.loadValidStudents(csvPath);
        System.out.println("   ✅ Loaded " + scoreReader.getStudentCount() + " students");
        
        // ================================================================
        // STEP 2: Find master submission ZIP (newest if multiple)
        // ================================================================
        System.out.println("\n📦 Step 2: Locating master submission ZIP...");
        Path submissionsDir = Paths.get("data", "input", "submissions");
        Path masterZip = findNewestZip(submissionsDir);
        System.out.println("   ✅ Using: " + masterZip.getFileName());
        
        // ================================================================
        // STEP 3: Extract master ZIP to temporary location
        // ================================================================
        System.out.println("\n📂 Step 3: Extracting master ZIP...");
        
        // Create temp directory in system temp folder
        // Files.createTempDirectory():
        // - Windows: C:\Users\Name\AppData\Local\Temp\master_extract_xxxxx
        // - Mac: /var/folders/.../master_extract_xxxxx
        // - Linux: /tmp/master_extract_xxxxx
        // The xxxxx is random to avoid conflicts
        Path tempExtract = Files.createTempDirectory("master_extract");
        
        try {
            // Extract master ZIP
            ZipFileProcessor.unzip(masterZip, tempExtract);
            
            // ================================================================
            // STEP 4: Find all student ZIPs inside the extracted master
            // ================================================================
            System.out.println("\n🔍 Step 4: Finding individual student submissions...");
            List<Path> studentZips = findStudentZips(tempExtract);
            
            if (studentZips.isEmpty()) {
                throw new IOException(
                    "❌ No student ZIPs found inside master ZIP!\n" +
                    "   Expected structure: student-submission.zip contains individual student ZIPs"
                );
            }
            
            System.out.println("   ✅ Found " + studentZips.size() + " student submissions");
            
            // ================================================================
            // STEP 5: Prepare final destination directory
            // ================================================================
            Path finalDestination = Paths.get("data", "extracted");
            Files.createDirectories(finalDestination);
            
            // ================================================================
            // STEP 6: Validate and extract each student (3-layer detection)
            // ================================================================
            System.out.println("\n👥 Step 5: Validating students (3-layer detection)...");
            System.out.println("-".repeat(70));
            
            List<ValidationResult> results = new ArrayList<>();
            int count = 0;
            
            // Process each student ZIP
            for (Path studentZip : studentZips) {
                count++;
                
                // Show progress: [1/6] [2/6] etc.
                System.out.print("   [" + count + "/" + studentZips.size() + "] ");
                
                // Run 3-layer validation
                ValidationResult result = validator.validate3Layer(
                    studentZip,
                    finalDestination,
                    scoreReader
                );
                
                results.add(result);
                logValidationResult(result);
            }
            
            // ================================================================
            // STEP 7: Print validation summary
            // ================================================================
            printValidationSummary(results);
            
            return results;
            
        } finally {
            // ================================================================
            // STEP 8: Cleanup temporary directory
            // ================================================================
            // IMPORTANT: This runs even if an exception occurred
            // We must clean up temp files to avoid filling up disk
            deleteDirectory(tempExtract);
            System.out.println("🧹 Cleaned up temporary files");
        }
    }
    
    /**
     * Finds the newest ZIP file in a directory.
     * 
     * STRATEGY:
     * 1. Find all .zip files in directory
     * 2. If none found → throw error
     * 3. If one found → return it
     * 4. If multiple found → compare modification times, return newest
     * 
     * USE CASE:
     * Instructor might upload multiple versions:
     * - student-submission.zip (uploaded Monday)
     * - student-submission-updated.zip (uploaded Wednesday)
     * System automatically uses the Wednesday version
     * 
     * CROSS-PLATFORM NOTES:
     * - Files.getLastModifiedTime() works on all platforms
     * - Returns FileTime object (timezone-aware, comparable)
     * - Handles different filesystem timestamps correctly
     * 
     * @param directory Directory to search for ZIPs
     * @return Path to the newest ZIP file
     * @throws IOException if directory doesn't exist or no ZIPs found
     */
    private Path findNewestZip(Path directory) throws IOException {
        
        // VALIDATION: Check if directory exists
        if (!Files.exists(directory)) {
            throw new IOException(
                "❌ Directory not found: " + directory + "\n" +
                "   Please create the directory and place student submissions there"
            );
        }
        
        // Find all ZIP files
        List<Path> zipFiles = new ArrayList<>();
        
        // DirectoryStream with glob pattern "*.zip"
        // More efficient than listing all files and filtering
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.zip")) {
            for (Path zip : stream) {
                zipFiles.add(zip);
            }
        }
        
        // VALIDATION: Check if any ZIPs found
        if (zipFiles.isEmpty()) {
            throw new IOException(
                "❌ No ZIP files found in: " + directory + "\n" +
                "   Please place student-submission.zip in this folder"
            );
        }
        
        // If only one ZIP, return it
        if (zipFiles.size() == 1) {
            return zipFiles.get(0);
        }
        
        // Multiple ZIPs found - find newest by modification time
        System.out.println("   ⚠️  Found " + zipFiles.size() + " ZIPs, selecting newest...");
        
        Path newest = zipFiles.get(0);
        FileTime newestTime = Files.getLastModifiedTime(newest);
        
        // Compare modification times
        for (int i = 1; i < zipFiles.size(); i++) {
            Path current = zipFiles.get(i);
            FileTime currentTime = Files.getLastModifiedTime(current);
            
            // FileTime.compareTo():
            // Returns > 0 if currentTime is newer than newestTime
            if (currentTime.compareTo(newestTime) > 0) {
                newest = current;
                newestTime = currentTime;
            }
        }
        
        return newest;
    }
    
    /**
     * Finds all student ZIP files in the extracted master ZIP.
     * 
     * HANDLES TWO COMMON STRUCTURES:
     * 
     * Structure A (with subfolder):
     *   student-submission.zip
     *   └── student-submission/
     *       ├── 2023-2024-ping.lee.2023.zip
     *       ├── 2023-2024-chee.teo.2022.zip
     *       └── ...
     * 
     * Structure B (flat):
     *   student-submission.zip
     *   ├── 2023-2024-ping.lee.2023.zip
     *   ├── 2023-2024-chee.teo.2022.zip
     *   └── ...
     * 
     * This method handles BOTH by checking subdirectories first
     * 
     * @param tempExtract Path to extracted master ZIP contents
     * @return List of paths to individual student ZIPs
     * @throws IOException if directory cannot be read
     */
    private List<Path> findStudentZips(Path tempExtract) throws IOException {
        
        List<Path> studentZips = new ArrayList<>();
        
        // Iterate through top-level contents
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(tempExtract)) {
            
            for (Path entry : stream) {
                
                if (Files.isDirectory(entry)) {
                    // It's a subdirectory - look for ZIPs inside
                    // This handles Structure A (nested folder)
                    try (DirectoryStream<Path> nested = Files.newDirectoryStream(entry, "*.zip")) {
                        for (Path zip : nested) {
                            studentZips.add(zip);
                        }
                    }
                    
                } else if (entry.toString().endsWith(".zip")) {
                    // It's a ZIP file at root level
                    // This handles Structure B (flat)
                    studentZips.add(entry);
                }
            }
        }
        
        return studentZips;
    }
    
    /**
     * Logs a single validation result with appropriate emoji and color.
     * 
     * OUTPUT FORMAT:
     * ✅ ping.lee.2023
     * ⚠️  chee.teo.2022 (recovered from folder)
     * ⚠️  david.2024 (recovered from comment)
     * ❌ wrongname.zip (UNRECOGNIZED)
     * 
     * @param result Validation result to log
     */
    private void logValidationResult(ValidationResult result) {
        
        switch (result.getStatus()) {
            
            case MATCHED:
                // Perfect - filename was correct
                System.out.println("✅ " + result.getResolvedId());
                break;
                
            case RECOVERED_FOLDER:
                // Found via internal folder name
                System.out.println("⚠️  " + result.getResolvedId() + " (recovered from folder)");
                break;
                
            case RECOVERED_COMMENT:
                // Found via Java file comment
                System.out.println("⚠️  " + result.getResolvedId() + " (recovered from comment)");
                break;
                
            case UNRECOGNIZED:
                // Could not identify student
                System.out.println("❌ " + result.getOriginalFilename() + " (UNRECOGNIZED)");
                break;
        }
    }
    
    /**
     * Prints a comprehensive validation summary table.
     * 
     * Shows:
     * - Total submissions processed
     * - How many were perfectly matched
     * - How many were recovered (and how)
     * - How many were unrecognized
     * - Warning if any unrecognized (won't be graded)
     * 
     * USES JAVA STREAMS:
     * - filter() to select specific statuses
     * - count() to get totals
     * - Functional programming style (modern Java)
     * 
     * @param results List of all validation results
     */
    private void printValidationSummary(List<ValidationResult> results) {
        
        System.out.println("\n" + "=".repeat(70));
        System.out.println("📊 VALIDATION SUMMARY");
        System.out.println("=".repeat(70));
        
        // Count results by status using streams
        long matched = results.stream()
            .filter(r -> r.getStatus() == ValidationResult.Status.MATCHED)
            .count();
            
        long recoveredFolder = results.stream()
            .filter(r -> r.getStatus() == ValidationResult.Status.RECOVERED_FOLDER)
            .count();
            
        long recoveredComment = results.stream()
            .filter(r -> r.getStatus() == ValidationResult.Status.RECOVERED_COMMENT)
            .count();
            
        long unrecognized = results.stream()
            .filter(r -> r.getStatus() == ValidationResult.Status.UNRECOGNIZED)
            .count();
        
        // Print summary
        System.out.println("Total submissions: " + results.size());
        System.out.println("  ✅ Matched (filename correct): " + matched);
        System.out.println("  ⚠️  Recovered (from folder): " + recoveredFolder);
        System.out.println("  ⚠️  Recovered (from comment): " + recoveredComment);
        System.out.println("  ❌ Unrecognized: " + unrecognized);
        
        // Warning if any unrecognized
        if (unrecognized > 0) {
            System.out.println("\n⚠️  WARNING: " + unrecognized + " unrecognized submission(s)");
            System.out.println("   These will NOT be graded. Manual investigation required.");
            System.out.println("   Check data/extracted/ for correctly extracted students.");
        }
        
        System.out.println("=".repeat(70) + "\n");
    }
    
    /**
     * Recursively deletes a directory and all its contents.
     * Used for cleanup of temporary directories.
     * 
     * IMPLEMENTATION:
     * - Files.walk() traverses entire directory tree
     * - sorted() in reverse order ensures files deleted before directories
     * - forEach() deletes each path
     * 
     * CROSS-PLATFORM:
     * - Works on all filesystems
     * - Handles different path separators automatically
     * - Safe for temporary directories
     * 
     * @param dir Directory to delete recursively
     * @throws IOException if deletion fails
     */
    private void deleteDirectory(Path dir) throws IOException {
        
        if (Files.exists(dir)) {
            
            Files.walk(dir)
                // Sort paths in reverse order (deepest first)
                // "/tmp/a/b/c.txt" comes before "/tmp/a/b" comes before "/tmp/a"
                .sorted((a, b) -> -a.compareTo(b))
                // Delete each path
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        // Log error but don't stop cleanup
                        // Some files might be locked or in use
                        System.err.println("⚠️  Could not delete: " + path);
                    }
                });
        }
    }
}