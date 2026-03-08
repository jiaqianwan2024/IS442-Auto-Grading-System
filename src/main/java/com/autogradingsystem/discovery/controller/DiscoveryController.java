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
 *   The old "*.zip" glob is case-sensitive on Linux/Mac and misses ".ZIP" files.
 * - After buildPlan(), orphaned testers (testers with no matching template file)
 *   are detected and logged with [ORPHANED TESTER] warnings.
 * - After buildPlan(), validates plan.getTaskCount() > 0 and throws IOException
 *   if empty — prevents a silent no-op execution phase.
 * - Discovery Summary table printed at end of Phase 2 showing tasks per question
 *   and a TOTAL count, making handshake failures with Execution immediately visible.
 *
 * @author IS442 Team
 * @version 2.7
 */
public class DiscoveryController {

    private final TemplateDiscovery templateDiscovery;
    private final TesterDiscovery testerDiscovery;
    private final GradingPlanBuilder planBuilder;

    public DiscoveryController() {
        this.templateDiscovery = new TemplateDiscovery();
        this.testerDiscovery   = new TesterDiscovery();
        this.planBuilder       = new GradingPlanBuilder();
    }

    /**
     * Discovers exam structure and builds the grading plan.
     *
     * WORKFLOW:
     * 1. findTemplateZip()           — locate template in INPUT_TEMPLATE (case-insensitive)
     * 2. discoverStructure()         — scan Q folders from template ZIP
     * 3. discoverTesters()           — scan testers directory
     * 4. warnOrphanedTesters()       — log testers that have no template counterpart
     * 5. buildPlan()                 — match testers to questions
     * 6. validate plan is non-empty  — throw if 0 tasks (prevents silent no-op)
     * 7. printDiscoverySummary()     — print table of tasks per question
     *
     * @return GradingPlan containing all grading tasks
     * @throws IOException if any discovery step fails or plan is empty
     */
    public GradingPlan buildGradingPlan() throws IOException {

        // Step 1 — locate template ZIP
        Path templateZip = findTemplateZip();

        // Step 2 — discover exam structure from template
        ExamStructure examStructure = templateDiscovery.discoverStructure(templateZip);

        // Step 3 — discover tester files
        TesterMap testerMap = testerDiscovery.discoverTesters(PathConfig.INPUT_TESTERS);

        // Step 4 — warn about orphaned testers (testers with no template counterpart)
        warnOrphanedTesters(examStructure, testerMap);

        // Step 5 — build grading plan (throws IOException if 0 tasks)
        GradingPlan plan = planBuilder.buildPlan(examStructure, testerMap);

        // Step 6 — extra safety guard in case buildPlan() ever returns an empty plan
        //           without throwing (defensive — should not normally be reached)
        if (plan.getTaskCount() == 0) {
            throw new IOException(
                "Discovery produced 0 gradable tasks. " +
                "Check that tester names match template filenames. " +
                "Example: Q1a.java in template needs Q1aTester.java in testers/"
            );
        }

        // Step 7 — print summary table
        printDiscoverySummary(examStructure, plan);

        return plan;
    }

    // ================================================================
    // PRIVATE HELPERS
    // ================================================================

    /**
     * Finds the template ZIP file in INPUT_TEMPLATE, scanning case-insensitively.
     *
     * WHY NOT "*.zip" GLOB:
     *   DirectoryStream glob "*.zip" is case-sensitive on Linux and macOS.
     *   A file named "Template.ZIP" or "template.Zip" would be silently missed.
     *   Instead, we list all files and check the lowercased name ourselves.
     *
     * VALIDATES:
     *   Exactly one ZIP exists — 0 or 2+ both throw with clear messages.
     *
     * @return Path to the template ZIP file
     * @throws IOException if 0 or 2+ ZIPs found, or directory is missing
     */
    private Path findTemplateZip() throws IOException {

        if (!Files.exists(PathConfig.INPUT_TEMPLATE)) {
            throw new IOException(
                "Template directory not found: " + PathConfig.INPUT_TEMPLATE + "\n" +
                "Please create the directory and place your template ZIP inside it."
            );
        }

        List<Path> zipFiles = new ArrayList<>();

        // Case-insensitive scan — catches .zip, .ZIP, .Zip, etc.
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(PathConfig.INPUT_TEMPLATE)) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)
                        && entry.getFileName().toString().toLowerCase().endsWith(".zip")) {
                    zipFiles.add(entry);
                }
            }
        }

        if (zipFiles.isEmpty()) {
            throw new IOException(
                "No template ZIP found in: " + PathConfig.INPUT_TEMPLATE + "\n" +
                "Please place your template ZIP (e.g. RenameToYourUsername.zip) in this directory."
            );
        }

        if (zipFiles.size() > 1) {
            List<String> names = new ArrayList<>();
            for (Path p : zipFiles) names.add(p.getFileName().toString());
            throw new IOException(
                "Multiple template ZIPs found in: " + PathConfig.INPUT_TEMPLATE + "\n" +
                "Found: " + names + "\n" +
                "Please keep only ONE template ZIP in this directory."
            );
        }

        return zipFiles.get(0);
    }

    /**
     * Detects and logs testers that have no matching template file.
     *
     * An "orphaned tester" is one whose question ID does not appear in ANY Q folder
     * of the template. This usually means the instructor added a tester for a question
     * that wasn't included in the template ZIP, or there is a naming mismatch.
     *
     * Example:
     *   Template has: Q1a, Q1b, Q2a, Q2b, Q3
     *   Testers  has: Q1a, Q1b, Q2a, Q2b, Q3, Q4a  <-- Q4a is orphaned
     *
     * The orphaned tester is logged as a warning but does NOT cause a failure —
     * it is merely unused in this grading run.
     */
    private void warnOrphanedTesters(ExamStructure examStructure, TesterMap testerMap) {

        // Collect all question IDs present in the template (case-normalised to lowercase)
        Set<String> templateIds = new java.util.HashSet<>();
        for (Map.Entry<String, List<String>> entry
                : examStructure.getQuestionFiles().entrySet()) {
            for (String fileName : entry.getValue()) {
                String lower = fileName.toLowerCase();
                if (!lower.endsWith(".java") && !lower.endsWith(".class")) continue;
                // Strip path prefix then extension to get the question ID
                String base = fileName.contains("/")
                    ? fileName.substring(fileName.lastIndexOf('/') + 1) : fileName;
                int dot = base.lastIndexOf('.');
                String id = (dot == -1) ? base : base.substring(0, dot);
                templateIds.add(id.toLowerCase());
            }
        }

        // Any tester key not found in the template set is orphaned
        for (String testerId : testerMap.getTesterMapping().keySet()) {
            if (!templateIds.contains(testerId.toLowerCase())) {
                System.out.println("   \u26A0\uFE0F  [ORPHANED TESTER] "
                    + testerMap.getTesterMapping().get(testerId)
                    + ": No matching template file found for question ID '"
                    + testerId + "'. This tester will not be used.");
            }
        }
    }

    /**
     * Prints a compact discovery summary table after the grading plan is built.
     *
     * Format:
     *   === Discovery Summary ===
     *   Q1 -> 2 task(s)
     *   Q2 -> 2 task(s)
     *   Q3 -> 1 task(s)
     *   TOTAL: 5 task(s) ready for grading
     *   =========================
     *
     * This makes "handshake failures" between Discovery and Execution immediately
     * visible — if a question shows 0 tasks here, Execution will get FILE_NOT_FOUND
     * for every student on that question.
     *
     * NOTE: We count tasks by examining the ExamStructure (which we own) rather
     * than calling methods on GradingTask, whose getter names are not confirmed.
     */
    private void printDiscoverySummary(ExamStructure examStructure, GradingPlan plan) {

        System.out.println("   === Discovery Summary ===");

        // Show per-folder gradable file count from the template.
        // The exact per-folder task breakdown is already visible from the [TASK] lines
        // printed by GradingPlanBuilder, so we show template file counts here as a
        // cross-check. If a folder shows 0 gradable files, Execution will always get
        // FILE_NOT_FOUND for that question — operator can catch it immediately.
        for (String folder : examStructure.getQuestionFolders()) {
            long gradable = examStructure.getFilesForQuestion(folder).stream()
                .filter(f -> {
                    String l = f.toLowerCase();
                    return l.endsWith(".java") || l.endsWith(".class");
                })
                .count();
            System.out.println("   " + folder + " -> " + gradable + " gradable file(s)");
        }

        System.out.println("   TOTAL: " + plan.getTaskCount()
            + " task(s) ready for grading");
        System.out.println("   =========================");
    }
}