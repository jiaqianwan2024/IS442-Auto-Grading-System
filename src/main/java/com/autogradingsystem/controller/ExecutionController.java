package com.autogradingsystem.controller;

import com.autogradingsystem.discovery.GradingPlanBuilder;
import com.autogradingsystem.discovery.TemplateDiscovery;
import com.autogradingsystem.discovery.TesterDiscovery;
import com.autogradingsystem.model.ExamStructure;
import com.autogradingsystem.model.GradingPlan;
import com.autogradingsystem.model.TesterMap;
import com.autogradingsystem.service.file.UnzipService;

import java.io.IOException;
import java.nio.file.*;

/**
 * ExecutionController - Main Orchestrator
 * 
 * PHASE 2 VERSION:
 * - Added initialize() method for Phase 1 + Phase 2
 * - Phase 3 grading logic will be added later
 * 
 * @author IS442 Team
 * @version 2.0 (Phase 2)
 */
public class ExecutionController {
    
    // =========================================================================
    // PHASE 2: Discovery Components
    // =========================================================================
    
    private UnzipService unzipService;
    private TemplateDiscovery templateDiscovery;
    private TesterDiscovery testerDiscovery;
    private GradingPlanBuilder planBuilder;
    
    // =========================================================================
    // PHASE 3: Grading Components (will be added later)
    // =========================================================================
    
    // TODO Phase 3: Add grading components
    // private TesterInjector injector;
    // private CompilerService compiler;
    // private ProcessRunner runner;
    // private OutputParser parser;
    
    /**
     * Constructor - Initialize all components
     */
    public ExecutionController() {
        // Phase 2: Initialize discovery components
        this.unzipService = new UnzipService();
        this.templateDiscovery = new TemplateDiscovery();
        this.testerDiscovery = new TesterDiscovery();
        this.planBuilder = new GradingPlanBuilder();
        
        // Phase 3: Will initialize grading components here
        // this.injector = new TesterInjector();
        // this.compiler = new CompilerService();
        // this.runner = new ProcessRunner();
        // this.parser = new OutputParser();
    }
    
    /**
     * Initialize system - Run Phase 1 + Phase 2
     * 
     * WORKFLOW:
     * 1. Phase 1: Extract and validate student submissions
     * 2. Phase 2: Discover exam structure from template
     * 3. Phase 2: Discover tester files
     * 4. Phase 2: Build grading plan
     * 5. Return grading plan (ready for Phase 3)
     * 
     * @return GradingPlan ready for execution
     * @throws IOException if files cannot be read or discovered
     */
    public GradingPlan initialize() throws IOException {
        
        System.out.println("\n" + "=".repeat(70));
        System.out.println("🚀 INITIALIZING AUTO-GRADING SYSTEM");
        System.out.println("=".repeat(70));
        
        // =====================================================================
        // PHASE 1: EXTRACTION & VALIDATION
        // =====================================================================
        
        System.out.println("\n=== PHASE 1: EXTRACTION & VALIDATION ===");
        unzipService.extractAndValidateStudents();
        System.out.println("✅ Phase 1 complete - Students extracted to data/extracted/");
        
        // =====================================================================
        // PHASE 2: DISCOVERY & PLANNING
        // =====================================================================
        
        System.out.println("\n=== PHASE 2: DISCOVERY & PLANNING ===");
        
        // Find template ZIP
        Path templateZip = findTemplateZip();
        
        // Discover exam structure from template
        ExamStructure structure = templateDiscovery.discoverStructure(templateZip);
        
        // Discover tester files
        Path testersDir = Paths.get("src", "main", "resources", "testers");
        TesterMap testers = testerDiscovery.discoverTesters(testersDir);
        
        // Build grading plan
        GradingPlan plan = planBuilder.buildPlan(structure, testers);
        
        // =====================================================================
        // SUMMARY
        // =====================================================================
        
        System.out.println("\n" + "=".repeat(70));
        System.out.println("✅ INITIALIZATION COMPLETE");
        System.out.println("=".repeat(70));
        System.out.println("Grading plan ready: " + plan.getSummary());
        
        // Warn if any testers are missing
        if (plan.getUngradableTaskCount() > 0) {
            System.err.println("\n⚠️  WARNING: " + plan.getUngradableTaskCount() + 
                             " task(s) missing testers - these will score 0");
        }
        
        System.out.println("=".repeat(70) + "\n");
        
        return plan;
    }
    
    /**
     * Helper method: Find template ZIP file
     * 
     * Looks in data/input/template/ for ZIP files
     * Returns first ZIP found
     * 
     * @return Path to template ZIP
     * @throws IOException if template not found
     */
    private Path findTemplateZip() throws IOException {
        
        Path templateDir = Paths.get("data", "input", "template");
        
        // Check if directory exists
        if (!Files.exists(templateDir)) {
            throw new IOException(
                "Template directory not found: " + templateDir + "\n" +
                "Please create directory and place template ZIP there"
            );
        }
        
        // Find all ZIP files
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(templateDir, "*.zip")) {
            
            Path firstZip = null;
            int count = 0;
            
            for (Path zip : stream) {
                if (firstZip == null) {
                    firstZip = zip;
                }
                count++;
            }
            
            if (firstZip == null) {
                throw new IOException(
                    "No ZIP files found in: " + templateDir + "\n" +
                    "Please place template ZIP (e.g., RenameToYourUsername.zip) there"
                );
            }
            
            if (count > 1) {
                System.out.println("   ⚠️  Multiple template ZIPs found, using: " + firstZip.getFileName());
            }
            
            return firstZip;
        }
    }
    
    // =========================================================================
    // PHASE 3: GRADING EXECUTION (TO BE IMPLEMENTED)
    // =========================================================================
    
    /**
     * Run grading - Phase 3 (NOT YET IMPLEMENTED)
     * 
     * This method will be updated in Phase 3 to:
     * - Accept GradingPlan parameter
     * - Get students from data/extracted/
     * - Use plan.getTasks() instead of hardcoded tasks
     * - Integrate with CompilerService, ProcessRunner, etc.
     * 
     * For now, this is a placeholder.
     */
    public void runGrading() {
        System.out.println("\n=== PHASE 3: GRADING ===");
        System.out.println("⚠️  Not implemented yet - will be added in Phase 3");
        System.out.println("This will compile and execute student code against testers\n");
    }
    
    /**
     * Run grading with plan - Phase 3 (TO BE IMPLEMENTED)
     * 
     * @param plan GradingPlan from initialize()
     */
    public void runGrading(GradingPlan plan) {
        // TODO Phase 3: Implement grading execution
        System.out.println("\n=== PHASE 3: GRADING ===");
        System.out.println("⚠️  Not implemented yet - will be added in Phase 3");
        System.out.println("Will grade " + plan.getTaskCount() + " tasks\n");
    }
}