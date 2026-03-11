package com.autogradingsystem;

import com.autogradingsystem.extraction.controller.ExtractionController;
import com.autogradingsystem.discovery.controller.DiscoveryController;
import com.autogradingsystem.execution.controller.ExecutionController;
import com.autogradingsystem.analysis.controller.AnalysisController;

import com.autogradingsystem.model.GradingPlan;
import com.autogradingsystem.model.GradingResult;


import java.util.List;

/**
 * Main - Central Orchestrator and Entry Point
 * 
 * PURPOSE:
 * - Acts as central hub coordinating all microservices
 * - Routes requests between services using PathConfig
 * - Provides unified logging and error handling
 * - Orchestrates complete grading workflow
 * 
 * ARCHITECTURE:
 * Main.java (this)
 *     ├─→ ExtractionController (Phase 1)
 *     ├─→ DiscoveryController (Phase 2)
 *     ├─→ ExecutionController (Phase 3)
 *     └─→ AnalysisController (Results processing)
 * 
 * WORKFLOW:
 * 1. Validate paths and setup
 * 2. Extract and validate students → ExtractionController
 * 3. Discover exam structure and testers → DiscoveryController
 * 4. Execute grading for all students → ExecutionController
 * 5. Analyze results and display → AnalysisController
 * 
 * @author IS442 Team
 * @version 4.0 (Spring Boot Microservices Structure)
 */
public class Main {

    public static void main(String[] args) {

        try {
            // ================================================================
            // HEADER
            // ================================================================
            printHeader();

            // ================================================================
            // INITIALIZATION & VALIDATION
            // ================================================================
            System.out.println("\n" + "=".repeat(70));
            System.out.println("🚀 INITIALIZING AUTO-GRADING SYSTEM");
            System.out.println("=".repeat(70));

            // Validate input paths
            System.out.println("\n📋 Step 1: Validating input paths...");
            if (!PathConfig.validateInputPaths()) {
                System.err.println("\n❌ Initialization failed: Missing required files");
                System.err.println("Please ensure all required files are in place:");
                System.err.println("  - config/IS442-ScoreSheet.csv");
                System.err.println("  - resources/input/submissions/");
                System.err.println("  - resources/input/template/");
                System.err.println("  - resources/input/testers/");
                return;
            }
            System.out.println("   ✅ All required paths validated");

            // Ensure output directories exist
            PathConfig.ensureOutputDirectories();

            // ================================================================
            // PHASE 1: EXTRACTION & VALIDATION
            // ================================================================
            System.out.println("\n📦 Step 2: Extracting and validating student submissions...");

            ExtractionController extractionController = new ExtractionController();
            int studentCount = extractionController.extractAndValidate();

            if (studentCount == 0) {
                System.err.println("\n❌ No valid students found. Exiting.");
                return;
            }

            System.out.println("   ✅ Extracted " + studentCount + " student(s)");

            // ================================================================
            // PHASE 2: DISCOVERY & PLANNING
            // ================================================================
            System.out.println("\n🔍 Step 3: Discovering exam structure and testers...");

            DiscoveryController discoveryController = new DiscoveryController();
            GradingPlan gradingPlan = discoveryController.buildGradingPlan();

            if (gradingPlan.getTasks().isEmpty()) {
                System.err.println("\n❌ No grading tasks found. Exiting.");
                return;
            }

            System.out.println("   ✅ Built grading plan with " + 
                             gradingPlan.getTaskCount() + " task(s)");

            // ================================================================
            // INITIALIZATION COMPLETE
            // ================================================================
            System.out.println("\n" + "=".repeat(70));
            System.out.println("✅ INITIALIZATION COMPLETE");
            System.out.println("=".repeat(70));

            // ================================================================
            // PHASE 3: GRADING EXECUTION
            // ================================================================
            System.out.println("\n" + "=".repeat(70));
            System.out.println("🎯 GRADING EXECUTION");
            System.out.println("=".repeat(70));

            ExecutionController executionController = new ExecutionController();
            List<GradingResult> results = executionController.gradeAllStudents(gradingPlan);

            System.out.println("\n" + "=".repeat(70));
            System.out.println("✅ GRADING COMPLETE");
            System.out.println("=".repeat(70));
            System.out.println("Total results: " + results.size());

            // ================================================================
            // ANALYSIS & REPORTING
            // ================================================================
            System.out.println("\n📊 ANALYZING RESULTS");

            AnalysisController analysisController = new AnalysisController();
            analysisController.analyzeAndDisplay(results);

            System.out.println("\n" + "=".repeat(70));
            System.out.println("✅ GRADING SYSTEM COMPLETE");
            System.out.println("=".repeat(70));

        } catch (Exception e) {
            System.err.println("\n❌ FATAL ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Prints the application header
     */
    private static void printHeader() {
        System.out.println("\n╔════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                     IS442 AUTO-GRADING SYSTEM                      ║");
        System.out.println("╚════════════════════════════════════════════════════════════════════╝");
    }
}