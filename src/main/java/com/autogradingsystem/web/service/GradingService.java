package com.autogradingsystem.web.service;  // ← CHANGED PACKAGE

import com.autogradingsystem.PathConfig;  // ← Core package (unchanged)
import com.autogradingsystem.extraction.controller.ExtractionController;  // ← Backend (unchanged)
import com.autogradingsystem.discovery.controller.DiscoveryController;  // ← Backend (unchanged)
import com.autogradingsystem.execution.controller.ExecutionController;  // ← Backend (unchanged)
import com.autogradingsystem.analysis.controller.AnalysisController;  // ← Backend (unchanged)
import com.autogradingsystem.model.GradingPlan;  // ← Shared model (unchanged)
import com.autogradingsystem.model.GradingResult;  // ← Shared model (unchanged)

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * GradingService - Wraps the existing grading pipeline for Spring Boot web layer
 * 
 * PACKAGE: com.autogradingsystem.web.service
 * PURPOSE: Bridge between web controller and backend pipeline
 * 
 * ARCHITECTURE:
 * GradingController (web request)
 *     → GradingService (this class - WEB LAYER)
 *         → ExtractionController (Phase 1 - BACKEND)
 *         → DiscoveryController  (Phase 2 - BACKEND)
 *         → ExecutionController  (Phase 3 - BACKEND)
 *         → AnalysisController   (Results - BACKEND)
 * 
 * LAYER SEPARATION:
 * - WEB LAYER: GradingController, GradingService (this package)
 * - BACKEND LAYER: extraction, discovery, execution, analysis (separate packages)
 * 
 * @author IS442 Team
 * @version 2.0 (Reorganized to web package)
 */
@Service
public class GradingService {

    /**
     * Runs the complete grading pipeline and returns results.
     * 
     * @return GradingReport containing results, logs, and status
     */
    public GradingReport runFullPipeline() {
        GradingReport report = new GradingReport();

        try {
            // ============================================================
            // STEP 1: Validate input paths
            // ============================================================
            report.addLog("Validating input paths...");
            if (!PathConfig.validateInputPaths()) {
                report.addLog("ERROR: Missing required input files.");
                report.addLog("Please ensure these exist:");
                report.addLog("  - config/IS442-ScoreSheet.csv");
                report.addLog("  - resources/input/submissions/");
                report.addLog("  - resources/input/template/");
                report.addLog("  - resources/input/testers/");
                report.setSuccess(false);
                return report;
            }
            report.addLog("All required paths validated.");
            PathConfig.ensureOutputDirectories();

            // ============================================================
            // STEP 2: Extraction & Validation (BACKEND)
            // ============================================================
            report.addLog("Extracting and validating student submissions...");
            ExtractionController extractionController = new ExtractionController();
            int studentCount = extractionController.extractAndValidate();

            if (studentCount == 0) {
                report.addLog("ERROR: No valid students found.");
                report.setSuccess(false);
                return report;
            }
            report.addLog("Extracted " + studentCount + " student(s).");
            report.setStudentCount(studentCount);

            // ============================================================
            // STEP 3: Discovery & Planning (BACKEND)
            // ============================================================
            report.addLog("Discovering exam structure and testers...");
            DiscoveryController discoveryController = new DiscoveryController();
            GradingPlan gradingPlan = discoveryController.buildGradingPlan();

            if (gradingPlan.getTasks().isEmpty()) {
                report.addLog("ERROR: No grading tasks found.");
                report.setSuccess(false);
                return report;
            }
            report.addLog("Built grading plan with " + gradingPlan.getTaskCount() + " task(s).");

            // ============================================================
            // STEP 4: Grading Execution (BACKEND)
            // ============================================================
            report.addLog("Running grading execution...");
            ExecutionController executionController = new ExecutionController();
            List<GradingResult> results = executionController.gradeAllStudents(gradingPlan);
            report.addLog("Grading complete. Total results: " + results.size());
            report.setResults(results);

            // ============================================================
            // STEP 5: Analysis & Reporting (BACKEND)
            // ============================================================
            report.addLog("Analyzing results and generating reports...");
            AnalysisController analysisController = new AnalysisController();
            analysisController.analyzeAndDisplay(results);
            report.addLog("Reports generated successfully.");

            report.setSuccess(true);
            report.addLog("GRADING PIPELINE COMPLETE.");

        } catch (Exception e) {
            report.addLog("FATAL ERROR: " + e.getMessage());
            report.setSuccess(false);
        }

        return report;
    }

    // ================================================================
    // Inner class: GradingReport
    // Holds everything the web UI needs to display
    // ================================================================
    public static class GradingReport {
        private boolean success;
        private int studentCount;
        private List<GradingResult> results = new ArrayList<>();
        private List<String> logs = new ArrayList<>();

        public void addLog(String message) {
            this.logs.add(message);
        }

        // --- Getters and Setters ---
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public int getStudentCount() { return studentCount; }
        public void setStudentCount(int studentCount) { this.studentCount = studentCount; }

        public List<GradingResult> getResults() { return results; }
        public void setResults(List<GradingResult> results) { this.results = results; }

        public List<String> getLogs() { return logs; }
    }
}
