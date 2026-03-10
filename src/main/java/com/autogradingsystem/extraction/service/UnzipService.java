package com.autogradingsystem.extraction.service;

import com.autogradingsystem.PathConfig;
import com.autogradingsystem.extraction.model.ValidationResult;

import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.io.IOException;
import java.util.*;

/**
 * UnzipService - Extraction Orchestrator (Resilient Version)
 * * PURPOSE:
 * - Coordinates student submission extraction workflow.
 * - Integrates: ZIP extraction + Student validation + Wrapper flattening.
 * - Resilient to missing questions (e.g., student only submits Q2 and Q3).
 * * TECHNICAL DETAILS:
 * - Uses recursive digging to find any valid question folder or file.
 * - Normalizes deep nesting to ensure Phase 3 can find source files.
 * - Protects against "NullPointerException" when students submit non-standard structures.
 * * @author IS442 Team
 * @version 4.1 (Resilient Extraction)
 */
public class UnzipService {
    
    private final StudentValidator validator;
    
    /**
     * Constructor - initializes validator.
     */
    public UnzipService() {
        this.validator = new StudentValidator();
    }
    
    /**
     * Extracts and validates all student submissions.
     * * @param submissionsDir Directory containing master ZIP.
     * @param extractedDir Directory to extract students to.
     * @param scoreReader ScoreSheetReader with valid students loaded.
     * @return List of ValidationResult objects.
     * @throws IOException if extraction fails.
     */
    public List<ValidationResult> extractAndValidateStudents(
            Path submissionsDir,
            Path extractedDir,
            ScoreSheetReader scoreReader) throws IOException {
        
        Path masterZip = findNewestZip(submissionsDir);
        Path tempExtract = Files.createTempDirectory("master-extract");
        
        try {
            ZipFileProcessor.unzip(masterZip, tempExtract);
            List<Path> studentZips = findStudentZips(tempExtract);
            
            if (studentZips.isEmpty()) {
                throw new IOException("No student ZIPs found inside master ZIP!");
            }
            
            List<ValidationResult> results = new ArrayList<>();
            
            for (Path studentZip : studentZips) {
                ValidationResult result = validator.validate3Layer(studentZip, extractedDir, scoreReader);
                results.add(result);
                
                if (result.isIdentified()) {
                    try {
                        Path extractedPath = extractedDir.resolve(result.getResolvedId());
                        flattenWrapperFolder(extractedPath);
                    } catch (Exception e) {
                        // Resiliency: Log failure for one student but continue the loop
                        System.err.println("⚠️  Warning: Non-standard structure for " + 
                                         result.getResolvedId() + ": " + e.getMessage());
                    }
                }
            }
            return results;
        } finally {
            deleteDirectory(tempExtract);
        }
    }

    /**
     * Flattens deep wrapper folders (e.g., username/wrapper/Q2/).
     * * WORKFLOW:
     * 1. Locate the "True Root" containing any Q items.
     * 2. Move all contents from True Root to student ID folder.
     * 3. Cleanup empty intermediate folders.
     * * @param studentDir Path to student's root directory.
     * @throws IOException if moving files fails.
     */
    private void flattenWrapperFolder(Path studentDir) throws IOException {
        if (!Files.exists(studentDir) || !Files.isDirectory(studentDir)) {
            return;
        }

        Path trueRoot = findTrueRoot(studentDir);

        // Only flatten if trueRoot is a nested subfolder
        if (trueRoot != null && !trueRoot.equals(studentDir)) {
            System.out.println("   🔧 Normalizing structure from: " + studentDir.relativize(trueRoot));
            
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(trueRoot)) {
                for (Path item : stream) {
                    Path target = studentDir.resolve(item.getFileName());
                    Files.move(item, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            cleanupEmptyWrappers(studentDir);
        }
    }

    /**
     * findTrueRoot - Recursively locates the directory containing student work.
     * * STRATEGY:
     * 1. Scans current folder for ANY item matching Q pattern (Q1, Q2, Q1.java, etc).
     * 2. If a "Q" item is found, this is the root (even if Q1 is missing).
     * 3. If no "Q" item is found but exactly one subfolder exists, recurse into it.
     * 4. Fallback: Return current path if we can't dig deeper.
     * * @param currentPath The directory to search within.
     * @return Path to the directory containing Q-items.
     */
    private Path findTrueRoot(Path currentPath) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(currentPath)) {
            for (Path item : stream) {
                String name = item.getFileName().toString();
                // Matches Q followed by any digits (Q1, Q2, Q3, etc)
                if (name.matches("^Q\\d+.*$")) {
                    return currentPath;
                }
            }
        }

        List<Path> subfolders = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(currentPath)) {
            for (Path item : stream) {
                if (Files.isDirectory(item) && !item.getFileName().toString().startsWith(".")) {
                    subfolders.add(item);
                }
            }
        }

        if (subfolders.size() == 1) {
            return findTrueRoot(subfolders.get(0));
        }

        // Return current path if no Q folders found and no single wrapper to follow
        return currentPath;
    }

    /**
     * cleanupEmptyWrappers - Removes redundant folders after flattening.
     * * SAFETY:
     * - Only deletes a directory if it is verified to be empty.
     * - This prevents accidental deletion of non-standard student folders.
     */
    private void cleanupEmptyWrappers(Path studentDir) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(studentDir)) {
            for (Path item : stream) {
                if (Files.isDirectory(item)) {
                    // Safety Check: Only delete if the folder is empty
                    try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(item)) {
                        if (!dirStream.iterator().hasNext()) {
                            Files.delete(item);
                        }
                    }
                }
            }
        }
    }

    /**
     * Finds the newest ZIP file in a directory.
     */
    private Path findNewestZip(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            throw new IOException("Directory not found: " + directory);
        }
        
        List<Path> zipFiles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.zip")) {
            for (Path zip : stream) { zipFiles.add(zip); }
        }
        
        if (zipFiles.isEmpty()) {
            throw new IOException("No ZIP files found in: " + directory);
        }
        
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
     * Finds all student ZIP files in the extracted master ZIP.
     */
    private List<Path> findStudentZips(Path tempExtract) throws IOException {
        List<Path> studentZips = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(tempExtract)) {
            for (Path item : stream) {
                if (Files.isDirectory(item)) {
                    try (DirectoryStream<Path> subStream = Files.newDirectoryStream(item, "*.zip")) {
                        for (Path zip : subStream) { studentZips.add(zip); }
                    }
                } else if (item.toString().endsWith(".zip")) {
                    studentZips.add(item);
                }
            }
        }
        return studentZips;
    }

    /**
     * Recursively deletes a directory and all its contents.
     */
    private void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        Files.walk(directory)
            .sorted(Comparator.reverseOrder())
            .forEach(path -> {
                try { Files.delete(path); } catch (IOException e) { }
            });
    }
}