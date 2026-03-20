package com.autogradingsystem.extraction.controller;

import com.autogradingsystem.PathConfig;
import com.autogradingsystem.extraction.service.UnzipService;
import com.autogradingsystem.extraction.service.ScoreSheetReader;
import com.autogradingsystem.extraction.model.ValidationResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    // ================================================================
    // PATH FIELDS — null means "use global PathConfig" (single-assessment)
    // Set by the path-aware constructor for multi-assessment runs
    // ================================================================

    private final Path csvScoresheet;
    private final Path inputSubmissions;
    private final Path outputExtracted;

    // ================================================================
    // CONSTRUCTORS
    // ================================================================

    /**
     * Default constructor — uses global PathConfig static paths.
     * Called by GradingService for the standard single-assessment flow.
     * Behaviour is identical to the original constructor.
     */
    public ExtractionController() {
        this.scoreReader      = new ScoreSheetReader();
        this.unzipService     = new UnzipService();
        this.csvScoresheet    = null;
        this.inputSubmissions = null;
        this.outputExtracted  = null;
    }

    /**
     * Path-aware constructor for multi-assessment support.
     * Called by AssessmentOrchestrator with per-assessment isolated paths
     * so that concurrent grading runs never touch each other's files.
     *
     * @param csvScoresheet    Path to the scoresheet CSV for this assessment
     * @param inputSubmissions Path to the submissions directory for this assessment
     * @param outputExtracted  Path to the extraction output directory for this assessment
     */
    public ExtractionController(Path csvScoresheet,
                                 Path inputSubmissions,
                                 Path outputExtracted) {
        this.scoreReader      = new ScoreSheetReader();
        this.unzipService     = new UnzipService();
        this.csvScoresheet    = csvScoresheet;
        this.inputSubmissions = inputSubmissions;
        this.outputExtracted  = outputExtracted;
    }

    // ================================================================
    // PATH RESOLUTION — picks instance path if set, falls back to PathConfig
    // ================================================================

    private Path resolveCsvScoresheet()    { return csvScoresheet    != null ? csvScoresheet    : PathConfig.CSV_SCORESHEET;    }
    private Path resolveInputSubmissions() { return inputSubmissions != null ? inputSubmissions : PathConfig.INPUT_SUBMISSIONS; }
    private Path resolveOutputExtracted()  { return outputExtracted  != null ? outputExtracted  : PathConfig.OUTPUT_EXTRACTED;  }

    // ================================================================
    // PUBLIC API
    // ================================================================

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
        scoreReader.loadValidStudents(resolveCsvScoresheet());

        // Extract and validate
        List<ValidationResult> results = unzipService.extractAndValidateStudents(
                resolveInputSubmissions(),
                resolveOutputExtracted(),
                scoreReader);

        // Count successful extractions
        long successCount = results.stream()
                .filter(r -> r.getResolvedId() != null) // counts both identified + unrecognized (now has rawName)
                .count();

        return (int) successCount;
    }

    // ================================================================
    // PRIVATE HELPERS
    // ================================================================

    /**
     * Cleans old extracted data if it exists.
     * Ensures fresh start for each grading run.
     */
    private void cleanOldData() throws IOException {
        Path extracted = resolveOutputExtracted();

        if (Files.exists(extracted)) {
            // Delete directory recursively
            Files.walk(extracted)
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
        Files.createDirectories(extracted);
    }
}