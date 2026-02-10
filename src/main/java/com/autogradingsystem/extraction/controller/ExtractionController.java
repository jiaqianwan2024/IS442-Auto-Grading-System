package com.autogradingsystem.extraction.controller;

import com.autogradingsystem.PathConfig;
import com.autogradingsystem.extraction.service.UnzipService;
import com.autogradingsystem.extraction.service.ScoreSheetReader;
import com.autogradingsystem.extraction.model.ValidationResult;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/**
 * ExtractionController - Brain for Extraction Service (Phase 1)
 * 
 * PURPOSE:
 * - Coordinates student extraction and validation workflow
 * - Acts as entry point for extraction service
 * - Called by Main.java during initialization
 * 
 * RESPONSIBILITIES:
 * - Clean old extracted data
 * - Load valid students from CSV
 * - Extract and validate student submissions
 * - Flatten wrapper folders
 * 
 * @author IS442 Team
 * @version 4.0
 */
public class ExtractionController {
    
    private final ScoreSheetReader scoreReader;
    private final UnzipService unzipService;
    
    /**
     * Constructor - initializes extraction services
     */
    public ExtractionController() {
        this.scoreReader = new ScoreSheetReader();
        this.unzipService = new UnzipService();
    }
    
    /**
     * Extracts and validates all student submissions
     * 
     * WORKFLOW:
     * 1. Clean old extracted data
     * 2. Load valid students from CSV
     * 3. Extract student ZIPs
     * 4. Validate students using 3-layer detection
     * 5. Flatten wrapper folders
     * 
     * @return Number of successfully extracted students
     * @throws IOException if extraction fails
     */
    public int extractAndValidate() throws IOException {
        
        // Clean old extracted data
        cleanOldData();
        
        // Load valid students from CSV
        scoreReader.loadValidStudents(PathConfig.CSV_SCORESHEET);
        
        // Extract and validate
        List<ValidationResult> results = unzipService.extractAndValidateStudents(
            PathConfig.INPUT_SUBMISSIONS,
            PathConfig.OUTPUT_EXTRACTED,
            scoreReader
        );
        
        // Count successful extractions
        long successCount = results.stream()
            .filter(ValidationResult::isIdentified)
            .count();
        
        return (int) successCount;
    }
    
    /**
     * Cleans old extracted data if it exists
     * Ensures fresh start for each grading run
     */
    private void cleanOldData() throws IOException {
        if (Files.exists(PathConfig.OUTPUT_EXTRACTED)) {
            // Delete directory recursively
            Files.walk(PathConfig.OUTPUT_EXTRACTED)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        // Ignore
                    }
                });
        }
        
        // Recreate empty directory
        Files.createDirectories(PathConfig.OUTPUT_EXTRACTED);
    }
}