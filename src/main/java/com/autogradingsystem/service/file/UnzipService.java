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
 * VERSION: 3.0 (Phase 3 - Wrapper Flattening)
 * 
 * NEW IN v3.0:
 * - FIX 1: Automatic wrapper folder flattening
 * - Detects and flattens nested structures (01400003/Q1/, ping.lee.2023/Q1/, etc.)
 * - Handles double-nesting (username folder inside username folder)
 * 
 * @author IS442 Team
 * @version 3.0
 */
public class UnzipService {
    
    // Service dependencies
    private final ScoreSheetReader scoreReader;
    private final StudentValidator validator;
    
    public UnzipService() {
        this.scoreReader = new ScoreSheetReader();
        this.validator = new StudentValidator();
    }
    
    /**
     * Complete extraction and validation workflow.
     * Now includes automatic wrapper folder flattening!
     */
    public List<ValidationResult> extractAndValidateStudents() throws IOException {
        
        System.out.println("\n" + "=".repeat(70));
        System.out.println("🚀 EXTRACTION & VALIDATION - Phase 1");
        System.out.println("=".repeat(70));
        
        // STEP 0: Clean up old extracted data
        Path extractedDir = Paths.get("data", "extracted");
        if (Files.exists(extractedDir)) {
            System.out.println("\n🧹 Step 0: Cleaning up previous extraction...");
            deleteDirectory(extractedDir);
            System.out.println("   ✅ Old data cleared");
        }
        Files.createDirectories(extractedDir);
        
        // STEP 1: Load official student list
        Path csvPath = Paths.get("config", "IS442-ScoreSheet.csv");
        
        if (!Files.exists(csvPath)) {
            throw new IOException(
                "❌ LMS scoresheet not found: " + csvPath + "\n" +
                "   Please place IS442-ScoreSheet.csv in the config/ folder"
            );
        }
        
        System.out.println("\n👥 Step 1: Loading official student list...");
        scoreReader.loadValidStudents(csvPath);
        
        // STEP 2: Find master submission ZIP
        System.out.println("\n📁 Step 2: Locating master submission ZIP...");
        Path submissionsDir = Paths.get("data", "input", "submissions");
        Path masterZip = findNewestZip(submissionsDir);
        System.out.println("   ✅ Using: " + masterZip.getFileName());
        
        // STEP 3: Extract master ZIP
        Path tempExtract = Files.createTempDirectory("master-extract");
        
        try {
            System.out.println("\n📦 Step 3: Extracting master ZIP...");
            ZipFileProcessor.unzip(masterZip, tempExtract);
            
            // STEP 4: Find student ZIPs
            System.out.println("\n🔍 Step 4: Finding individual student submissions...");
            List<Path> studentZips = findStudentZips(tempExtract);
            
            if (studentZips.isEmpty()) {
                throw new IOException(
                    "❌ No student ZIPs found inside master ZIP!\n" +
                    "   Expected structure: student-submission.zip contains individual student ZIPs"
                );
            }
            
            System.out.println("   ✅ Found " + studentZips.size() + " student submissions");
            
            // STEP 5: Prepare destination
            Path finalDestination = Paths.get("data", "extracted");
            Files.createDirectories(finalDestination);
            
            // STEP 6: Validate and extract each student
            System.out.println("\n👥 Step 5: Validating students (3-layer detection)...");
            System.out.println("-".repeat(70));
            
            List<ValidationResult> results = new ArrayList<>();
            int count = 0;
            
            for (Path studentZip : studentZips) {
                count++;
                System.out.print("   [" + count + "/" + studentZips.size() + "] ");
                
                // Run 3-layer validation
                ValidationResult result = validator.validate3Layer(
                    studentZip,
                    finalDestination,
                    scoreReader
                );
                
                results.add(result);
                logValidationResult(result);
                
                // FIX 1: Flatten wrapper folders after extraction
                if (result.isIdentified()) {
                    try {
                        // Build path to extracted student folder
                        Path extractedPath = finalDestination.resolve(result.getResolvedId());
                        flattenWrapperFolder(extractedPath);
                    } catch (IOException e) {
                        System.err.println("⚠️  Warning: Could not flatten wrapper folder: " + e.getMessage());
                    }
                }
            }
            
            // STEP 7: Print summary
            printValidationSummary(results);
            
            return results;
            
        } finally {
            // STEP 8: Cleanup
            deleteDirectory(tempExtract);
            System.out.println("🧹 Cleaned up temporary files");
        }
    }
    
    /**
     * FIX 1: Flattens wrapper folders (e.g., 01400003/Q1/, ping.lee.2023/Q1/)
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
     * - Double nesting (username/username/Q1/)
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
        
        // Debug: Show what we found
        System.out.println("   🔍 Checking structure: " + folders.size() + " folder(s), " + files.size() + " file(s)");
        
        // Detect wrapper: exactly 1 folder and few/no real files
        if (folders.size() != 1 || files.size() > 2) {
            System.out.println("   ℹ️  No wrapper detected - structure looks correct");
            return;  // Not a wrapper structure
        }
        
        Path wrapperFolder = folders.get(0);
        System.out.println("   🔍 Potential wrapper: " + wrapperFolder.getFileName());
        
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
                        System.out.println("   ✓ Found: " + name);
                    }
                }
            }
        }
        
        // Only flatten if wrapper contains question folders
        if (!hasQuestionFolders) {
            System.out.println("   ℹ️  No Q folders in wrapper - skipping flatten");
            return;
        }
        
        // Flatten: Move all items from wrapper to parent
        System.out.println("   🔧 Flattening wrapper folder: " + wrapperFolder.getFileName());
        
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
            System.out.println("      ↳ Moved: " + item.getFileName());
        }
        
        // Delete empty wrapper folder
        Files.delete(wrapperFolder);
        System.out.println("   ✅ Structure flattened successfully");
    }
    
    private Path findNewestZip(Path directory) throws IOException {
        
        if (!Files.exists(directory)) {
            throw new IOException(
                "❌ Directory not found: " + directory + "\n" +
                "   Please create the directory and place student submissions there"
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
                "❌ No ZIP files found in: " + directory + "\n" +
                "   Please place student-submission.zip in this folder"
            );
        }
        
        if (zipFiles.size() == 1) {
            return zipFiles.get(0);
        }
        
        System.out.println("   ⚠️  Found " + zipFiles.size() + " ZIPs, selecting newest...");
        
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
    
    private List<Path> findStudentZips(Path tempExtract) throws IOException {
        List<Path> studentZips = new ArrayList<>();
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(tempExtract)) {
            for (Path item : stream) {
                if (Files.isDirectory(item)) {
                    try (DirectoryStream<Path> subStream = Files.newDirectoryStream(item, "*.zip")) {
                        for (Path zip : subStream) {
                            studentZips.add(zip);
                        }
                    }
                } else if (item.toString().endsWith(".zip")) {
                    studentZips.add(item);
                }
            }
        }
        
        return studentZips;
    }
    
    private void logValidationResult(ValidationResult result) {
        ValidationResult.Status status = result.getStatus();
        String symbol = "";
        
        switch (status) {
            case MATCHED:
                symbol = "✅";
                System.out.println(symbol + " " + result.getResolvedId());
                break;
            case RECOVERED_FOLDER:
                symbol = "⚠️ ";
                System.out.println(symbol + " " + result.getResolvedId() + " (recovered from folder)");
                break;
            case RECOVERED_COMMENT:
                symbol = "⚠️ ";
                System.out.println(symbol + " " + result.getResolvedId() + " (recovered from comment)");
                break;
            case UNRECOGNIZED:
                symbol = "❌";
                System.out.println(symbol + " " + result.getOriginalFilename() + " (UNRECOGNIZED)");
                break;
        }
    }
    
    private void printValidationSummary(List<ValidationResult> results) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("📊 VALIDATION SUMMARY");
        System.out.println("=".repeat(70));
        
        long matched = results.stream()
            .filter(r -> r.getStatus() == ValidationResult.Status.MATCHED)
            .count();
        long fromFolder = results.stream()
            .filter(r -> r.getStatus() == ValidationResult.Status.RECOVERED_FOLDER)
            .count();
        long fromComment = results.stream()
            .filter(r -> r.getStatus() == ValidationResult.Status.RECOVERED_COMMENT)
            .count();
        long unrecognized = results.stream()
            .filter(r -> r.getStatus() == ValidationResult.Status.UNRECOGNIZED)
            .count();
        
        System.out.println("Total submissions: " + results.size());
        System.out.println("  ✅ Matched (filename correct): " + matched);
        System.out.println("  ⚠️  Recovered (from folder): " + fromFolder);
        System.out.println("  ⚠️  Recovered (from comment): " + fromComment);
        System.out.println("  ❌ Unrecognized: " + unrecognized);
        System.out.println("=".repeat(70));
    }
    
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