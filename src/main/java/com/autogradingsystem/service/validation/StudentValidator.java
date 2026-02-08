package com.autogradingsystem.service.validation;

import com.autogradingsystem.service.file.ZipFileProcessor;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * StudentValidator - 3-Layer Student Identification System
 * 
 * PURPOSE:
 * - Validates and extracts student submissions with intelligent ID detection
 * - Handles common student mistakes (wrong filename, forgot to rename folder)
 * - Uses 3-layer fallback strategy to recover student identity
 * 
 * THE PROBLEM THIS SOLVES:
 * Students are supposed to:
 * 1. Rename "RenameToYourUsername" folder to their ID (e.g., "ping.lee.2023")
 * 2. Name the ZIP file with their ID (e.g., "2023-2024-ping.lee.2023.zip")
 * 
 * But students make mistakes:
 * - Wrong ZIP filename: "2023-2024-mysubmission.zip" (typo!)
 * - Forgot to rename folder: Still named "RenameToYourUsername"
 * - Forgot to add name in Java files
 * 
 * This class tries 3 different ways to figure out who submitted:
 * 
 * LAYER 1: Check ZIP filename
 * LAYER 2: Check folder name inside ZIP
 * LAYER 3: Scan Java files for "Email ID:" in comments
 * LAYER 4: Give up - mark as UNRECOGNIZED
 * 
 * @author IS442 Team
 * @version 2.0 (Refactored with Path API)
 */
public class StudentValidator {
    
    public ValidationResult validate3Layer(
            Path studentZip, 
            Path destinationFolder,
            ScoreSheetReader scoreReader) throws IOException {
        
        String originalName = studentZip.getFileName().toString();
        String cleanZipName = originalName.replace(".zip", "").replace("2023-2024-", "");  
        
        // LAYER 1: VALIDATE FILENAME
        if (scoreReader.isValid(cleanZipName)) {
            Path studentDest = destinationFolder.resolve(cleanZipName);
            ZipFileProcessor.unzip(studentZip, studentDest);
            return new ValidationResult(originalName, ValidationResult.Status.MATCHED, cleanZipName);
        }
        
        // LAYER 2: VALIDATE INTERNAL FOLDER NAME
        Path tempPath = Files.createTempDirectory("temp_check_" + cleanZipName);
        try {
            ZipFileProcessor.unzip(studentZip, tempPath);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(tempPath)) {
                for (Path entry : stream) {
                    if (Files.isDirectory(entry)) {
                        String folderName = entry.getFileName().toString();
                        if (scoreReader.isValid(folderName)) {
                            Path finalDest = destinationFolder.resolve(folderName);
                            Files.move(entry, finalDest, StandardCopyOption.REPLACE_EXISTING);
                            return new ValidationResult(originalName, ValidationResult.Status.RECOVERED_FOLDER, folderName);
                        }
                    }
                }
            }
            
            // LAYER 3: DEEP SCAN SOURCE CODE FOR EMAIL
            String discoveredId = scanJavaFilesForEmail(tempPath, scoreReader);
            if (discoveredId != null) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(tempPath)) {
                    for (Path entry : stream) {
                        if (Files.isDirectory(entry)) {
                            Path finalDest = destinationFolder.resolve(discoveredId);
                            Files.move(entry, finalDest, StandardCopyOption.REPLACE_EXISTING);
                            return new ValidationResult(originalName, ValidationResult.Status.RECOVERED_COMMENT, discoveredId);
                        }
                    }
                }
            }
        } finally {
            deleteDirectory(tempPath);
        }
        
        // LAYER 4: UNRECOGNIZED
        return new ValidationResult(originalName, ValidationResult.Status.UNRECOGNIZED, "N/A");
    }
    
    private String scanJavaFilesForEmail(Path folder, ScoreSheetReader scoreReader) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        findJavaFilesRecursive(folder, javaFiles);
        
        for (Path javaFile : javaFiles) {
            try (BufferedReader br = Files.newBufferedReader(javaFile)) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.contains("Email ID:")) {
                        String[] parts = line.split("Email ID:");
                        if (parts.length > 1) {
                            String extracted = parts[1].trim();
                            if (scoreReader.isValid(extracted)) {
                                return extracted;
                            }
                        }
                    }
                }
            } catch (IOException ignored) {}
        }
        return null;
    }
    
    private void findJavaFilesRecursive(Path dir, List<Path> javaFiles) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    findJavaFilesRecursive(entry, javaFiles);
                } else if (entry.toString().endsWith(".java")) {
                    javaFiles.add(entry);
                }
            }
        }
    }
    
    private void deleteDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            Files.walk(dir).sorted((a, b) -> -a.compareTo(b)).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    System.err.println("⚠️  Failed to delete: " + path);
                }
            });
        }
    }
    
    public static class ValidationResult {
        public enum Status {
            MATCHED, RECOVERED_FOLDER, RECOVERED_COMMENT, UNRECOGNIZED
        }
        
        private final String originalFilename;
        private final Status status;
        private final String resolvedId;
        
        public ValidationResult(String originalFilename, Status status, String resolvedId) {
            this.originalFilename = originalFilename;
            this.status = status;
            this.resolvedId = resolvedId;
        }
        
        public String getOriginalFilename() { return originalFilename; }
        public Status getStatus() { return status; }
        public String getResolvedId() { return resolvedId; }
        public boolean isIdentified() { return status != Status.UNRECOGNIZED; }
        public boolean wasRecovered() { 
            return status == Status.RECOVERED_FOLDER || status == Status.RECOVERED_COMMENT;
        }
    }
}