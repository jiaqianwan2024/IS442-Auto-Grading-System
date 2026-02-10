package com.autogradingsystem.discovery.controller;

import com.autogradingsystem.PathConfig;
import com.autogradingsystem.discovery.service.TemplateDiscovery;
import com.autogradingsystem.discovery.service.TesterDiscovery;
import com.autogradingsystem.discovery.service.GradingPlanBuilder;
import com.autogradingsystem.discovery.model.ExamStructure;
import com.autogradingsystem.discovery.model.TesterMap;
import com.autogradingsystem.model.GradingPlan;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * DiscoveryController - Brain for Discovery Service (Phase 2)
 * 
 * PURPOSE:
 * - Coordinates exam structure discovery workflow
 * - Acts as entry point for discovery service
 * - Called by Main.java during initialization
 * 
 * RESPONSIBILITIES:
 * - Discover exam structure from template ZIP
 * - Discover tester files
 * - Match testers to questions
 * - Build complete grading plan
 * 
 * @author IS442 Team
 * @version 4.0
 */
public class DiscoveryController {
    
    private final TemplateDiscovery templateDiscovery;
    private final TesterDiscovery testerDiscovery;
    private final GradingPlanBuilder planBuilder;
    
    /**
     * Constructor - initializes discovery services
     */
    public DiscoveryController() {
        this.templateDiscovery = new TemplateDiscovery();
        this.testerDiscovery = new TesterDiscovery();
        this.planBuilder = new GradingPlanBuilder();
    }
    
    /**
     * Discovers exam structure and builds grading plan
     * 
     * WORKFLOW:
     * 1. Find template ZIP in INPUT_TEMPLATE directory
     * 2. Discover exam structure (questions and files)
     * 3. Discover tester files
     * 4. Match testers to questions
     * 5. Build and return GradingPlan
     * 
     * @return GradingPlan containing all grading tasks
     * @throws IOException if discovery fails
     */
    public GradingPlan buildGradingPlan() throws IOException {
        
        // Find template ZIP
        Path templateZip = findTemplateZip();
        
        // Discover exam structure
        ExamStructure examStructure = templateDiscovery.discoverStructure(templateZip);
        
        // Discover testers
        TesterMap testerMap = testerDiscovery.discoverTesters(PathConfig.INPUT_TESTERS);
        
        // Build grading plan
        GradingPlan plan = planBuilder.buildPlan(examStructure, testerMap);
        
        return plan;
    }
    
    /**
     * Finds the template ZIP file in INPUT_TEMPLATE directory
     * 
     * EXPECTED: RenameToYourUsername.zip or similar template file
     * 
     * @return Path to template ZIP file
     * @throws IOException if template not found or multiple templates exist
     */
    private Path findTemplateZip() throws IOException {
        
        if (!Files.exists(PathConfig.INPUT_TEMPLATE)) {
            throw new IOException("Template directory not found: " + PathConfig.INPUT_TEMPLATE);
        }
        
        // Find all ZIP files in template directory
        Path templateZip = null;
        int zipCount = 0;
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(
                PathConfig.INPUT_TEMPLATE, "*.zip")) {
            
            for (Path zip : stream) {
                templateZip = zip;
                zipCount++;
            }
        }
        
        if (zipCount == 0) {
            throw new IOException(
                "No template ZIP found in: " + PathConfig.INPUT_TEMPLATE + "\n" +
                "Please place template ZIP (e.g., RenameToYourUsername.zip) in this directory"
            );
        }
        
        if (zipCount > 1) {
            throw new IOException(
                "Multiple template ZIPs found in: " + PathConfig.INPUT_TEMPLATE + "\n" +
                "Please keep only one template ZIP file"
            );
        }
        
        return templateZip;
    }
}