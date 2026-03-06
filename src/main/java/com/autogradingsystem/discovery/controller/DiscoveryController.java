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
import java.util.Set;

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
 * - Report orphaned testers and print discovery summary [Fix-7, Fix-14 v2.6]
 *
 * @author IS442 Team
 * @version 4.0
 */
public class DiscoveryController {

    private final TemplateDiscovery templateDiscovery;
    private final TesterDiscovery testerDiscovery;
    private final GradingPlanBuilder planBuilder;

    public DiscoveryController() {
        this.templateDiscovery = new TemplateDiscovery();
        this.testerDiscovery = new TesterDiscovery();
        this.planBuilder = new GradingPlanBuilder();
    }

    /**
     * Discovers exam structure and builds grading plan.
     *
     * WORKFLOW:
     * 1. findTemplateZip()        - locate .zip in INPUT_TEMPLATE (case-insensitive)
     * 2. discoverStructure()      - scan template ZIP for Q folders and files
     * 3. discoverTesters()        - scan testers directory for *Tester.java files
     * 4. buildPlan()              - match testers to template files
     * 5. Validate plan non-empty  - throw if 0 tasks (DC-2)
     * 6. Warn orphaned testers    - testers with no template match [Fix-7]
     * 7. Print summary table      - concise plan overview [Fix-14]
     */
    public GradingPlan buildGradingPlan() throws IOException {

        Path templateZip = findTemplateZip();
        ExamStructure examStructure = templateDiscovery.discoverStructure(templateZip);
        TesterMap testerMap = testerDiscovery.discoverTesters(PathConfig.INPUT_TESTERS);
        GradingPlan plan = planBuilder.buildPlan(examStructure, testerMap);

        // DC-2: empty plan guard
        if (plan.getTaskCount() == 0) {
            throw new IOException(
                "Discovery failed: Grading plan contains 0 tasks!\n" +
                "No tester files could be matched to template question files.\n" +
                "Check that tester filenames follow the convention: Q1aTester.java for Q1a.java"
            );
        }

        // [Fix-7] Detect orphaned testers: registered by TesterDiscovery but never
        // matched any template file during buildPlan(). Without this, Q5Tester.java
        // placed in the testers folder but with no Q5/ in the template is silently ignored.
        Set<String> templateQuestionIds = examStructure.getQuestionFiles().values().stream()
            .flatMap(java.util.List::stream)
            .filter(f -> f.toLowerCase().endsWith(".java") || f.toLowerCase().endsWith(".class"))
            .map(f -> {
                String base = f.contains("/") ? f.substring(f.lastIndexOf('/') + 1) : f;
                int dot = base.lastIndexOf('.');
                return (dot == -1 ? base : base.substring(0, dot)).toLowerCase();
            })
            .collect(java.util.stream.Collectors.toSet());

        Set<String> orphaned = testerMap.findOrphanedTesters(templateQuestionIds);
        if (!orphaned.isEmpty()) {
            System.out.println("   [ORPHANED TESTERS] Registered but no matching template file:");
            orphaned.stream().sorted().forEach(id ->
                System.out.println("      - " + testerMap.getTesterMapping().get(id)
                    + "  (ID '" + id + "' not found in template)"));
            System.out.println("   Add Q folder + source to template, or remove the tester.");
        }

        // [Fix-14] Concise summary table — lets operator validate the full plan
        // at a glance without scrolling through per-file discovery log lines.
        System.out.println();
        System.out.println("   === Discovery Summary ===");
        // Count matched tasks per folder using examStructure + testerMap directly.
        // This avoids calling any GradingTask getter (getTasks/getQuestionId/getQuestionFolder)
        // which may not exist depending on the GradingTask implementation version.
        examStructure.getQuestionFolders().forEach(folder -> {
            long matchedInFolder = examStructure.getFilesForQuestion(folder).stream()
                .filter(f -> {
                    String lower = f.toLowerCase();
                    return lower.endsWith(".java") || lower.endsWith(".class");
                })
                .map(f -> {
                    String base = f.contains("/") ? f.substring(f.lastIndexOf('/') + 1) : f;
                    int dot = base.lastIndexOf('.');
                    return (dot == -1 ? base : base.substring(0, dot)).toLowerCase();
                })
                .filter(id -> testerMap.getTesterMapping().containsKey(id))
                .distinct()
                .count();
            System.out.println("   " + folder + " -> " + matchedInFolder + " task(s)");
        });
        if (!orphaned.isEmpty()) {
            System.out.println("   WARNING: " + orphaned.size() + " orphaned tester(s) ignored");
        }
        System.out.println("   TOTAL: " + plan.getTaskCount() + " task(s) ready for grading");
        System.out.println("   =========================");
        System.out.println();

        return plan;
    }

    /**
     * Finds the template ZIP file in INPUT_TEMPLATE directory.
     *
     * [Fix-8] Uses case-insensitive .endsWith(".zip") check instead of "*.zip" glob.
     * The DirectoryStream glob is case-sensitive on Linux — "Template.ZIP" exported on
     * Windows would cause a confusing "No template ZIP found" error without this fix.
     */
    private Path findTemplateZip() throws IOException {

        if (!Files.exists(PathConfig.INPUT_TEMPLATE)) {
            throw new IOException("Template directory not found: " + PathConfig.INPUT_TEMPLATE);
        }

        Path templateZip = null;
        int zipCount = 0;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(PathConfig.INPUT_TEMPLATE)) {
            for (Path file : stream) {
                if (Files.isRegularFile(file)
                        && file.getFileName().toString().toLowerCase().endsWith(".zip")) {
                    templateZip = file;
                    zipCount++;
                }
            }
        }

        if (zipCount == 0) {
            throw new IOException(
                "No template ZIP found in: " + PathConfig.INPUT_TEMPLATE + "\n" +
                "Please place template ZIP (e.g., RenameToYourUsername.zip) in this directory\n" +
                "Note: .ZIP (uppercase) is also accepted"
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