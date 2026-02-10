package com.autogradingsystem;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * PathConfig - Centralized Path Management and Service Routing
 * 
 * PURPOSE:
 * - Provides centralized access to all resource paths
 * - Acts as configuration hub for Main.java to route services
 * - Single source of truth for file system locations
 * 
 * DESIGN:
 * - All paths are relative to project root
 * - Uses Java Path API for cross-platform compatibility
 * - Organized by input/output categories
 * 
 * @author IS442 Team
 * @version 4.0 (Spring Boot Microservices Structure)
 */
public class PathConfig {
    
    // ================================================================
    // CONFIGURATION PATHS
    // ================================================================
    
    /**
     * Path to LMS scoresheet CSV file
     * Contains official list of enrolled students
     */
    public static final Path CSV_SCORESHEET = Paths.get("config", "IS442-ScoreSheet.csv");
    
    // ================================================================
    // INPUT RESOURCE PATHS
    // ================================================================
    
    /**
     * Base directory for all input resources
     */
    public static final Path INPUT_BASE = Paths.get("resources", "input");
    
    /**
     * Directory containing student submission ZIP files
     * Expected: student-submission.zip (master ZIP with all students)
     */
    public static final Path INPUT_SUBMISSIONS = INPUT_BASE.resolve("submissions");
    
    /**
     * Directory containing template/answer key ZIP
     * Expected: RenameToYourUsername.zip
     */
    public static final Path INPUT_TEMPLATE = INPUT_BASE.resolve("template");
    
    /**
     * Directory containing tester files
     * Expected: Q1aTester.java, Q1bTester.java, etc.
     */
    public static final Path INPUT_TESTERS = INPUT_BASE.resolve("testers");
    
    // ================================================================
    // OUTPUT RESOURCE PATHS
    // ================================================================
    
    /**
     * Base directory for all output resources
     */
    public static final Path OUTPUT_BASE = Paths.get("resources", "output");
    
    /**
     * Directory where extracted student submissions are stored
     * Format: extracted/{username}/Q1/Q1a.java
     */
    public static final Path OUTPUT_EXTRACTED = OUTPUT_BASE.resolve("extracted");
    
    /**
     * Directory for generated reports (future use)
     */
    public static final Path OUTPUT_REPORTS = OUTPUT_BASE.resolve("reports");
    
    // ================================================================
    // HELPER METHODS
    // ================================================================
    
    /**
     * Builds path to a specific student's root folder
     * 
     * @param username Student username (e.g., "chee.teo.2022")
     * @return Path to student's extracted folder
     */
    public static Path getStudentFolder(String username) {
        return OUTPUT_EXTRACTED.resolve(username);
    }
    
    /**
     * Builds path to a specific student's question folder
     * 
     * @param username Student username
     * @param questionFolder Question folder name (e.g., "Q1")
     * @return Path to student's question folder
     */
    public static Path getStudentQuestionFolder(String username, String questionFolder) {
        return OUTPUT_EXTRACTED.resolve(username).resolve(questionFolder);
    }
    
    /**
     * Validates that all required input paths exist
     * Called by Main.java during initialization
     * 
     * @return true if all required paths exist, false otherwise
     */
    public static boolean validateInputPaths() {
        // Check CSV exists
        if (!CSV_SCORESHEET.toFile().exists()) {
            System.err.println("❌ Missing: " + CSV_SCORESHEET);
            return false;
        }
        
        // Check input directories exist
        if (!INPUT_SUBMISSIONS.toFile().exists()) {
            System.err.println("❌ Missing directory: " + INPUT_SUBMISSIONS);
            return false;
        }
        
        if (!INPUT_TEMPLATE.toFile().exists()) {
            System.err.println("❌ Missing directory: " + INPUT_TEMPLATE);
            return false;
        }
        
        if (!INPUT_TESTERS.toFile().exists()) {
            System.err.println("❌ Missing directory: " + INPUT_TESTERS);
            return false;
        }
        
        return true;
    }
    
    /**
     * Ensures all output directories exist
     * Creates them if they don't exist
     * Called by Main.java during initialization
     */
    public static void ensureOutputDirectories() {
        OUTPUT_BASE.toFile().mkdirs();
        OUTPUT_EXTRACTED.toFile().mkdirs();
        OUTPUT_REPORTS.toFile().mkdirs();
    }
}