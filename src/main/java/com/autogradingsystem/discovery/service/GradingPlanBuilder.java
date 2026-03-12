package com.autogradingsystem.discovery.service;

import com.autogradingsystem.discovery.model.ExamStructure;
import com.autogradingsystem.discovery.model.TesterMap;
import com.autogradingsystem.model.GradingTask;
import com.autogradingsystem.model.GradingPlan;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GradingPlanBuilder - Matches Testers to Questions and Builds Grading Plan
 *
 * PURPOSE:
 * - Takes discovered exam structure and tester map
 * - Matches each template question file with its corresponding tester
 * - Builds a complete GradingPlan with all GradingTask objects
 *
 * MATCHING LOGIC:
 * - Question file : Q1a.java  -> Question ID: Q1a
 * - Tester file   : Q1aTester.java -> Question ID: Q1a
 * - Match: Q1a == Q1a (case-insensitive) -> Create GradingTask
 *
 * CHANGES IN v2.7:
 * - resolveTemplateId() private helper introduced (DRY fix):
 *     strips leading path segments ("src/Q1a.java" -> "Q1a") then extension.
 *     Used by BOTH buildPlan() and isCompatible() so they can never disagree.
 * - O(1) tester lookup via HashMap.get(id.toLowerCase()) instead of O(n) loop.
 *     TesterMap keys are already normalised to lowercase by TesterDiscovery.
 * - assignedTaskIds Set prevents both Q1a.java AND Q1a.class from creating
 *     two GradingTask objects (double-score bug). .java wins; .class is [DUPLICATE].
 * - Per-folder [WARNING] when a Q folder has gradable files but produced 0 tasks.
 * - Empty-plan guard: if tasks.isEmpty() after all folders, logs a clear message
 *     and throws IOException — execution phase never runs with nothing to grade.
 * - isCompatible() uses resolveTemplateId() + lowercase get(), identical to buildPlan().
 *
 * @author IS442 Team
 * @version 2.7
 */
public class GradingPlanBuilder {

    /**
     * Builds complete grading plan from exam structure and testers.
     *
     * WORKFLOW:
     * 1. For each question folder in ExamStructure (Q1, Q2, Q3):
     *    a. For each file in the folder:
     *       i.  Skip system junk (.DS_Store, __MACOSX)
     *       ii. Skip data files (.txt, .csv) — log as [DATA FILE]
     *       iii.Extract question ID via resolveTemplateId()
     *       iv. O(1) tester lookup (case-insensitive via lowercase key)
     *       v.  If already assigned -> log [DUPLICATE] and skip
     *       vi. If matched -> create GradingTask, mark ID as assigned
     *       vii.If no match -> log [HELPER] (used for compilation only)
     *    b. After each folder: if it had gradable files but 0 tasks, log [WARNING]
     * 2. After all folders: if 0 tasks total, log clear message and throw IOException
     *
     * @param structure ExamStructure from TemplateDiscovery
     * @param testerMap TesterMap from TesterDiscovery
     * @return GradingPlan with all matched tasks
     * @throws IOException if no tasks could be matched
     */
    public GradingPlan buildPlan(ExamStructure structure, TesterMap testerMap)
            throws IOException {

        List<GradingTask> tasks = new ArrayList<>();
        Map<String, List<String>> questionFiles = structure.getQuestionFiles();

        // Tracks which question IDs already have a GradingTask.
        // Prevents Q1a.java AND Q1a.class from both creating tasks (double-score bug).
        // .java is processed first (alphabetical sort in TemplateDiscovery), so it wins.
        Set<String> assignedTaskIds = new HashSet<>();

        System.out.println("   \uD83D\uDD0D Finalizing Grading Plan...");

        for (String questionFolder : questionFiles.keySet()) {
            List<String> filesInFolder = questionFiles.get(questionFolder);
            int tasksBeforeFolder = tasks.size();

            for (String fileName : filesInFolder) {

                // 1. Skip OS/system junk silently
                if (fileName.equalsIgnoreCase(".DS_Store")
                        || fileName.contains("__MACOSX")) continue;

                // 2. Skip data files — they are dependencies, not gradeable submissions
                String lower = fileName.toLowerCase();
                if (!lower.endsWith(".java") && !lower.endsWith(".class")) {
                    System.out.println("      \u2139\uFE0F  [DATA FILE] " + fileName
                        + ": Required dependency for " + questionFolder + ".");
                    continue;
                }

                // 3. Resolve clean question ID.
                //    resolveTemplateId() handles both flat ("Q1a.java") and nested
                //    ("src/Q1a.java") paths.  Single source of truth — same method
                //    used by isCompatible() below.
                String templateName = resolveTemplateId(fileName);

                // 4. O(1) tester lookup.
                //    TesterDiscovery normalises all keys to lowercase, so we look up
                //    with lowercase to guarantee a match regardless of original casing.
                String matchedTester = testerMap.getTesterMapping()
                        .get(templateName.toLowerCase());

                // 5. Categorise
                if (matchedTester != null) {

                    // Guard: skip if this question ID already has a task (.java wins)
                    if (assignedTaskIds.contains(templateName.toLowerCase())) {
                        System.out.println("      \u26A0\uFE0F  [DUPLICATE] " + fileName
                            + ": Task for '" + templateName
                            + "' already assigned (preferring .java). Skipping.");
                        continue;
                    }

                    tasks.add(new GradingTask(templateName, matchedTester,
                            questionFolder, fileName));
                    assignedTaskIds.add(templateName.toLowerCase());
                    System.out.println("      \u2705 [TASK] " + templateName
                        + ": Matched with answer key " + matchedTester + ".");

                } else {
                    System.out.println("      \uD83D\uDCA1 [HELPER] " + templateName
                        + ": Supporting code found. Used for compilation only.");
                }
            }

            // Folder-level fallback: if no individual file matched a tester,
            // check if the FOLDER NAME itself matches a tester.
            // Handles Q4-style questions where files are nested in subfolders
            // (e.g. Q4/resource/Application.java) so no file name matches "Q4".
            int tasksFromFolder = tasks.size() - tasksBeforeFolder;
            boolean folderHadGradableFiles = filesInFolder.stream()
                .anyMatch(f -> f.toLowerCase().endsWith(".java")
                             || f.toLowerCase().endsWith(".class"));

            if (folderHadGradableFiles && tasksFromFolder == 0) {
                String folderTester = testerMap.getTesterMapping()
                        .get(questionFolder.toLowerCase());

                if (folderTester != null && !assignedTaskIds.contains(questionFolder.toLowerCase())) {
                    String representativeFile = filesInFolder.stream()
                        .filter(f -> f.toLowerCase().endsWith(".java"))
                        .findFirst()
                        .orElse(filesInFolder.get(0));
                    if (representativeFile.contains("/")) {
                        representativeFile = representativeFile.substring(representativeFile.lastIndexOf('/') + 1);
                    }
                    tasks.add(new GradingTask(questionFolder, folderTester,
                            questionFolder, representativeFile));
                    assignedTaskIds.add(questionFolder.toLowerCase());
                    System.out.println("      ✅ [FOLDER-TASK] " + questionFolder
                        + ": Matched entire folder with tester " + folderTester + ".");
                } else {
                    System.out.println("      ⚠️  [WARNING] " + questionFolder
                        + ": Found gradable files but no matching tester. "
                        + "Entire folder skipped. "
                        + "Ensure a tester like '" + questionFolder + "Tester.java' "
                        + "exists in the testers directory.");
                }
            }
        }

        // Empty-plan guard: never let execution run with zero tasks
        if (tasks.isEmpty()) {
            System.out.println("   \u26A0\uFE0F  WARNING: 0 gradable tasks could be mapped!");
            System.out.println("      Check that tester filenames match template filenames.");
            System.out.println("      Example: Q1a.java in template needs Q1aTester.java in testers/");
            throw new IOException(
                "Grading plan is empty — no template files matched any tester.\n" +
                "Ensure tester names follow the convention: Q<n>[a-z]Tester.java\n" +
                "Example: Q1aTester.java matches Q1a.java in the template."
            );
        }

        System.out.println("   \uD83C\uDFC1 Successfully mapped " + tasks.size()
            + " gradable task(s).");
        return new GradingPlan(tasks);
    }

    /**
     * Pre-check: returns true if at least one tester-question match is possible.
     *
     * Uses exactly the same resolveTemplateId() + lowercase get() logic as buildPlan()
     * so that isCompatible() and buildPlan() can never disagree about what matches.
     *
     * @param structure ExamStructure to validate
     * @param testerMap TesterMap to validate
     * @return true if at least one match found, false otherwise
     */
    public boolean isCompatible(ExamStructure structure, TesterMap testerMap) {

        if (structure.getQuestionFiles().isEmpty()) return false;
        if (testerMap.getTesterMapping().isEmpty()) return false;

        for (List<String> files : structure.getQuestionFiles().values()) {
            for (String file : files) {
                if (file.equalsIgnoreCase(".DS_Store")
                        || file.contains("__MACOSX")) continue;

                String fileLower = file.toLowerCase();
                if (!fileLower.endsWith(".java") && !fileLower.endsWith(".class")) continue;

                String templateName = resolveTemplateId(file);
                if (testerMap.getTesterMapping()
                        .containsKey(templateName.toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }

    // ================================================================
    // PRIVATE HELPER
    // ================================================================

    /**
     * Resolves a raw filename (or relative path) to a clean question ID.
     *
     * STEP 1 — strip path prefix:
     *   "src/Q1a.java"  ->  "Q1a.java"
     *   "Q1a.java"      ->  "Q1a.java"  (unchanged)
     *
     * STEP 2 — strip extension:
     *   "Q1a.java"  ->  "Q1a"
     *   "Q1a.class" ->  "Q1a"
     *   "Q1a"       ->  "Q1a"  (no dot, returned as-is)
     *
     * This is the single source of truth for filename -> questionId conversion.
     * Both buildPlan() and isCompatible() call this method so they always agree.
     *
     * @param fileName raw filename or relative path from ExamStructure
     * @return clean question ID (e.g. "Q1a")
     */
    private String resolveTemplateId(String fileName) {
        // Strip any leading path segments
        String baseName = fileName.contains("/")
            ? fileName.substring(fileName.lastIndexOf('/') + 1)
            : fileName;
        // Strip extension
        int dot = baseName.lastIndexOf('.');
        return (dot == -1) ? baseName : baseName.substring(0, dot);
    }
}