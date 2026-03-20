package com.autogradingsystem.web.controller;

import com.autogradingsystem.web.service.GradingService;
import com.autogradingsystem.web.service.GradingService.GradingReport;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/**
 * GradingController - Handles all web UI requests
 * 
 * PACKAGE: com.autogradingsystem.web.controller
 * PURPOSE: Web layer controller (Spring MVC)
 * 
 * ROUTES:
 *   GET  /                → Home page (single-page app)
 *   POST /upload          → Handle 4 file uploads (session-persisted)
 *   POST /grade           → Run grading pipeline (returns JSON)
 *   GET  /download/csv    → Download score sheet CSV
 *   GET  /download/excel  → Download statistics Excel
 *   GET  /check-uploads   → Check which files are already uploaded
 * 
 * FILE UPLOADS (4 required):
 *   1. submission   → resources/input/submissions/student-submission.zip
 *   2. template     → resources/input/template/RenameToYourUsername.zip
 *   3. scoresheet   → config/IS442-ScoreSheet.csv
 *   4. testers      → resources/input/testers.zip (extracted to testers/)
 * 
 * @author IS442 Team
 * @version 2.0 (Reorganized to web package)
 */
@Controller
public class GradingController {

    @Autowired
    private GradingService gradingService;

    /**
     * Home page - single-page application
     */
    @GetMapping("/")
    public String home() {
        return "index"; 
    }

    @GetMapping("/multi-test")
    public String multiTest() {
        return "multi-assessment-test";
    }

    /**
     * Handle multiple file uploads
     * Supports uploading all 4 files at once or individually
     * Files persist until manually replaced
     * 
     * @return JSON response with upload status
     */
    @PostMapping("/upload")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleUpload(
            @RequestParam(value = "submission", required = false) MultipartFile submissionZip,
            @RequestParam(value = "template", required = false) MultipartFile templateZip,
            @RequestParam(value = "scoresheet", required = false) MultipartFile scoreSheetCsv,
            @RequestParam(value = "testers", required = false) MultipartFile testersZip) {
        
        Map<String, Object> response = new HashMap<>();
        Map<String, String> uploaded = new HashMap<>();
        StringBuilder messages = new StringBuilder();

        try {
            // Upload 1: Student Submissions ZIP
            if (submissionZip != null && !submissionZip.isEmpty()) {
                Path destination = Paths.get("resources/input/submissions/student-submission.zip");
                Files.createDirectories(destination.getParent());
                Files.copy(submissionZip.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
                uploaded.put("submission", submissionZip.getOriginalFilename());
                messages.append("✓ Student submissions uploaded. ");
            }

            // Upload 2: Template ZIP
            if (templateZip != null && !templateZip.isEmpty()) {
                Path destination = Paths.get("resources/input/template/RenameToYourUsername.zip");
                Files.createDirectories(destination.getParent());
                Files.copy(templateZip.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
                uploaded.put("template", templateZip.getOriginalFilename());
                messages.append("✓ Template uploaded. ");
            }

            // Upload 3: Score Sheet CSV
            if (scoreSheetCsv != null && !scoreSheetCsv.isEmpty()) {
                Path destination = Paths.get("config/IS442-ScoreSheet.csv");
                Files.createDirectories(destination.getParent());
                Files.copy(scoreSheetCsv.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
                uploaded.put("scoresheet", scoreSheetCsv.getOriginalFilename());
                messages.append("✓ Score sheet uploaded. ");
            }

            // Upload 4: Testers ZIP (extract to testers/ folder)
            if (testersZip != null && !testersZip.isEmpty()) {
                // Save ZIP temporarily
                Path tempZip = Paths.get("resources/input/testers-temp.zip");
                Files.createDirectories(tempZip.getParent());
                Files.copy(testersZip.getInputStream(), tempZip, StandardCopyOption.REPLACE_EXISTING);
                
                // Extract ZIP to testers/ folder
                Path testersDir = Paths.get("resources/input/testers");
                if (Files.exists(testersDir)) {
                    // Clear existing testers
                    Files.walk(testersDir)
                         .sorted((a, b) -> -a.compareTo(b))
                         .forEach(path -> {
                             try { Files.delete(path); } catch (IOException e) {}
                         });
                }
                Files.createDirectories(testersDir);
                
                // Extract ZIP
                extractZip(tempZip, testersDir);
                
                // NEW: Flatten the directory to remove wrapper folders
                flattenDirectory(testersDir);
                
                // Delete temp ZIP
                Files.deleteIfExists(tempZip);
                
                uploaded.put("testers", testersZip.getOriginalFilename());
                messages.append("✓ Test cases uploaded and extracted. ");
            }

            if (uploaded.isEmpty()) {
                response.put("success", false);
                response.put("message", "No files were uploaded");
                return ResponseEntity.badRequest().body(response);
            }

            response.put("success", true);
            response.put("message", messages.toString());
            response.put("uploaded", uploaded);
            return ResponseEntity.ok(response);

        } catch (IOException e) {
            response.put("success", false);
            response.put("message", "Upload failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Check which files are already uploaded (for session persistence)
     * 
     * @return JSON with file existence status
     */
    @GetMapping("/check-uploads")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkUploads() {
        Map<String, Object> response = new HashMap<>();
        Map<String, Boolean> exists = new HashMap<>();

        exists.put("submission", Files.exists(Paths.get("resources/input/submissions/student-submission.zip")));
        exists.put("template", Files.exists(Paths.get("resources/input/template/RenameToYourUsername.zip")));
        exists.put("scoresheet", Files.exists(Paths.get("config/IS442-ScoreSheet.csv")));
        exists.put("testers", Files.exists(Paths.get("resources/input/testers")) && 
                             hasJavaFiles(Paths.get("resources/input/testers")));

        int uploadedCount = (int) exists.values().stream().filter(v -> v).count();

        response.put("exists", exists);
        response.put("uploadedCount", uploadedCount);
        response.put("ready", uploadedCount == 4);

        return ResponseEntity.ok(response);
    }

    /**
     * Run the grading pipeline
     * Returns JSON for AJAX consumption
     */
    @PostMapping("/grade")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> runGrading() {
        GradingReport report = gradingService.runFullPipeline();

        Map<String, Object> response = new HashMap<>();
        response.put("success", report.isSuccess());
        response.put("studentCount", report.getStudentCount());
        response.put("logs", report.getLogs());

        if (report.getResults() != null && !report.getResults().isEmpty()) {
            response.put("taskCount", report.getResults().size());
            
            // Calculate statistics
            double totalScore = 0;
            double totalMax = 0;
            int perfectCount = 0;
            
            for (var result : report.getResults()) {
                totalScore += result.getScore();
                totalMax += result.getMaxScore();
                if (result.isPerfect()) perfectCount++;
            }
            
            double averageScore = totalMax > 0 ? (totalScore / totalMax) * 100 : 0;
            double successRate = report.getResults().size() > 0 ? 
                (perfectCount * 100.0 / report.getResults().size()) : 0;
            
            response.put("averageScore", averageScore);
            response.put("successRate", successRate);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Download the updated score sheet CSV
     */
    @GetMapping("/download/csv")
    public ResponseEntity<Resource> downloadCsv() {
        Path file = Paths.get("resources/output/reports/IS442-ScoreSheet-Updated.csv");
        
        if (!Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(file.toFile());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=IS442-ScoreSheet-Updated.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(resource);
    }

    /**
     * Download the statistics Excel report
     */
    @GetMapping("/download/excel")
    public ResponseEntity<Resource> downloadExcel() {
        Path file = Paths.get("resources/output/reports/IS442-Statistics.xlsx");
        
        if (!Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(file.toFile());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=IS442-Statistics.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(resource);
    }

    // ================================================================
    // Helper Methods
    // ================================================================

    /**
     * Extract ZIP file
     */
    private void extractZip(Path zipFile, Path destDir) throws IOException {
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                Files.newInputStream(zipFile))) {
            
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path targetPath = destDir.resolve(entry.getName());
                
                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(zis, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    /**
     * Check if directory contains .java files
     */
    private boolean hasJavaFiles(Path dir) {
        try {
            return Files.walk(dir)
                    .anyMatch(path -> path.toString().endsWith(".java"));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Flattens a directory by moving all nested files to the root,
     * deleting macOS hidden files, and removing leftover empty directories.
     */
    private void flattenDirectory(Path rootDir) throws IOException {
        // 1. Find all regular files no matter how deep they are nested
        java.util.List<Path> allFiles = Files.walk(rootDir)
                .filter(Files::isRegularFile)
                .collect(java.util.stream.Collectors.toList());

        // 2. Process every file
        for (Path file : allFiles) {
            String fileName = file.getFileName().toString();
            
            // NEW: Delete macOS metadata files and hidden files
            if (fileName.startsWith(".")) {
                Files.delete(file);
                continue;
            }

            Path target = rootDir.resolve(fileName);
            // Only move it if it isn't already in the root
            if (!file.getParent().equals(rootDir)) {
                Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        // 3. Clean up and delete all the empty subdirectories
        Files.walk(rootDir)
                .filter(Files::isDirectory)
                .filter(p -> !p.equals(rootDir))
                .sorted((a, b) -> b.compareTo(a)) // Sort reverse to delete deepest folders first
                .forEach(p -> {
                    try { Files.delete(p); } catch (IOException e) {}
                });
    }
}
