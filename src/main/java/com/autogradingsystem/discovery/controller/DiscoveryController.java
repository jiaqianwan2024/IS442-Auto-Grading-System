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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DiscoveryController - Brain for Discovery Service (Phase 2)
 *
 * PURPOSE:
 * - Coordinates exam structure discovery workflow
 * - Acts as entry point for the discovery service
 * - Called by Main.java during initialization
 *
 * RESPONSIBILITIES:
 * - Discover exam structure from template ZIP
 * - Discover tester files
 * - Match testers to questions and build grading plan
 * - Validate plan is non-empty before returning to Main
 *
 * CHANGES IN v2.7:
 * - findTemplateZip() now scans case-insensitively for .zip/.ZIP files.
 * - Orphaned tester warnings logged after buildPlan().
 * - Validates plan.getTaskCount() > 0, throws if empty.
 * - Discovery Summary table printed at end of Phase 2.
 *
 * CHANGES IN v2.8 (multi-assessment):
 * - Added path-aware constructor accepting inputTemplate + inputTesters paths.
 * - resolve*() helpers fall back to PathConfig when paths are null (single-assessment).
 *
 * @author IS442 Team
 * @version 2.8
 */
public class DiscoveryController {

    private final TemplateDiscovery templateDiscovery;
    private final TesterDiscovery   testerDiscovery;
    private final GradingPlanBuilder planBuilder;

    // ================================================================
    // PATH FIELDS — null means "use global PathConfig" (single-assessment)
    // ================================================================

    private final Path inputTemplate;
    private final Path inputTesters;

    // ================================================================
    // CONSTRUCTORS
    // ================================================================

    /**
     * Default constructor — uses global PathConfig static paths.
     * Called by GradingService for the standard single-assessment flow.
     * Behaviour is identical to the original constructor.
     */
    public DiscoveryController() {
        this.templateDiscovery = new TemplateDiscovery();
        this.testerDiscovery   = new TesterDiscovery();
        this.planBuilder       = new GradingPlanBuilder();
        this.inputTemplate     = null;
        this.inputTesters      = null;
    }

    /**
     * Path-aware constructor for multi-assessment support.
     * Called by AssessmentOrchestrator with per-assessment isolated paths.
     *
     * @param inputTemplate Path to the template directory for this assessment
     * @param inputTesters  Path to the testers directory for this assessment
     */
    public DiscoveryController(Path inputTemplate, Path inputTesters) {
        this.templateDiscovery = new TemplateDiscovery();
        this.testerDiscovery   = new TesterDiscovery();
        this.planBuilder       = new GradingPlanBuilder();
        this.inputTemplate     = inputTemplate;
        this.inputTesters      = inputTesters;
    }

    // ================================================================
    // PATH RESOLUTION
    // ================================================================

    private Path resolveInputTemplate() { return inputTemplate != null ? inputTemplate : PathConfig.INPUT_TEMPLATE; }
    private Path resolveInputTesters()  { return inputTesters  != null ? inputTesters  : PathConfig.INPUT_TESTERS;  }

    // ================================================================
    // PUBLIC API
    // ================================================================

    /**
     * Discovers exam structure and builds the grading plan.
     *
     * WORKFLOW:
     * 1. findTemplateZip()           — locate template in INPUT_TEMPLATE (case-insensitive)
     * 2. discoverStructure()         — scan Q folders from template ZIP
     * 3. discoverTesters()           — scan testers directory
     * 4. warnOrphanedTesters()       — log testers with no template counterpart
     * 5. buildPlan()                 — match testers to questions
     * 6. validate plan is non-empty  — throw if 0 tasks
     * 7. printDiscoverySummary()     — print table of tasks per question
     *
     * @return GradingPlan containing all grading tasks
     * @throws IOException if any discovery step fails or plan is empty
     */
    public GradingPlan buildGradingPlan() throws IOException {

        Path templateZip    = findTemplateZip();
        ExamStructure examStructure = templateDiscovery.discoverStructure(templateZip);
        TesterMap testerMap = testerDiscovery.discoverTesters(resolveInputTesters());

        warnOrphanedTesters(examStructure, testerMap);

        GradingPlan plan = planBuilder.buildPlan(examStructure, testerMap);

        if (plan.getTaskCount() == 0) {
            throw new IOException(
                "Discovery produced 0 gradable tasks. " +
                "Check that tester names match template filenames. " +
                "Example: Q1a.java in template needs Q1aTester.java in testers/"
            );
        }

        printDiscoverySummary(examStructure, plan);

        return plan;
    }

    // ================================================================
    // PRIVATE HELPERS
    // ================================================================

    /**
     * Finds the template ZIP file in INPUT_TEMPLATE, scanning case-insensitively.
     */
    private Path findTemplateZip() throws IOException {
        Path templateDir = resolveInputTemplate();

        if (!Files.exists(templateDir)) {
            throw new IOException(
                "Template directory not found: " + templateDir + "\n" +
                "Please create the directory and place your template ZIP inside it."
            );
        }

        List<Path> zipFiles = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(templateDir)) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)
                        && entry.getFileName().toString().toLowerCase().endsWith(".zip")) {
                    zipFiles.add(entry);
                }
            }
        }

        if (zipFiles.isEmpty()) {
            throw new IOException(
                "No template ZIP found in: " + templateDir + "\n" +
                "Please place your template ZIP (e.g. RenameToYourUsername.zip) in this directory."
            );
        }

        if (zipFiles.size() > 1) {
            List<String> names = new ArrayList<>();
            for (Path p : zipFiles) names.add(p.getFileName().toString());
            throw new IOException(
                "Multiple template ZIPs found in: " + templateDir + "\n" +
                "Found: " + names + "\n" +
                "Please keep only ONE template ZIP in this directory."
            );
        }

        return zipFiles.get(0);
    }

    private void warnOrphanedTesters(ExamStructure examStructure, TesterMap testerMap) {
        Set<String> templateIds = new java.util.HashSet<>();
        for (Map.Entry<String, List<String>> entry : examStructure.getQuestionFiles().entrySet()) {
            for (String fileName : entry.getValue()) {
                String lower = fileName.toLowerCase();
                if (!lower.endsWith(".java") && !lower.endsWith(".class")) continue;
                String base = fileName.contains("/")
                    ? fileName.substring(fileName.lastIndexOf('/') + 1) : fileName;
                int dot = base.lastIndexOf('.');
                String id = (dot == -1) ? base : base.substring(0, dot);
                templateIds.add(id.toLowerCase());
            }
        }

        for (String testerId : testerMap.getTesterMapping().keySet()) {
            if (!templateIds.contains(testerId.toLowerCase())) {
                System.out.println("   \u26A0\uFE0F  [ORPHANED TESTER] "
                    + testerMap.getTesterMapping().get(testerId)
                    + ": No matching template file found for question ID '"
                    + testerId + "'. This tester will not be used.");
            }
        }
    }

    private void printDiscoverySummary(ExamStructure examStructure, GradingPlan plan) {
        System.out.println("   === Discovery Summary ===");
        for (String folder : examStructure.getQuestionFolders()) {
            long gradable = examStructure.getFilesForQuestion(folder).stream()
                .filter(f -> { String l = f.toLowerCase(); return l.endsWith(".java") || l.endsWith(".class"); })
                .count();
            System.out.println("   " + folder + " -> " + gradable + " gradable file(s)");
        }
        System.out.println("   TOTAL: " + plan.getTaskCount() + " task(s) ready for grading");
        System.out.println("   =========================");
    }
}