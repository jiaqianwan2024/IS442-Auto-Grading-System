package com.autogradingsystem.discovery.service;

import com.autogradingsystem.discovery.model.ExamStructure;
import com.autogradingsystem.discovery.model.TesterMap;
import com.autogradingsystem.model.GradingTask;
import com.autogradingsystem.model.GradingPlan;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GradingPlanBuilder - Matches Testers to Questions and Builds Grading Plan
 * * PURPOSE:
 * - Takes discovered exam structure and testers
 * - Matches each question file with its corresponding tester
 * - Builds complete GradingPlan with all GradingTask objects
 * * MATCHING LOGIC:
 * - Question file: Q1a.java → Question ID: Q1a
 * - Tester file: Q1aTester.java → Question ID: Q1a
 * - Match: Q1a == Q1a → Create GradingTask ✅
 * * HANDLES EDGE CASES:
 * - Question without tester → Warning + Skip
 * - Tester without question → Ignored (not used)
 * - Helper files → Ignored (not graded directly)
 * * CHANGES FROM v3.0:
 * - Removed verbose logging (handled by DiscoveryController/Main.java)
 * - Keeps important warnings (missing testers)
 * - Cleaner method signatures
 * * @author IS442 Team
 * @version 4.0 (Spring Boot Microservices Structure)
 */
public class GradingPlanBuilder {
    
    /**
     * Builds complete grading plan from exam structure and testers
     * * WORKFLOW:
     * 1. For each question folder (Q1, Q2, Q3):
     * a. For each .java file in folder:
     * i. Extract question ID from filename
     * ii. Look up tester for this question ID
     * iii. If tester found → Create GradingTask
     * iv. If no tester → Warn and skip
     * 2. Collect all GradingTask objects
     * 3. Build and return GradingPlan
     * * EXAMPLE:
     * * ExamStructure:
     * Q1: [Q1a.java, Q1b.java]
     * Q2: [Q2a.java, Q2b.java]
     * Q3: [Q3.java, ShapeComparator.java]
     * * TesterMap:
     * Q1a → Q1aTester.java
     * Q1b → Q1bTester.java
     * Q2a → Q2aTester.java
     * Q2b → Q2bTester.java
     * Q3 → Q3Tester.java
     * * Output GradingPlan contains:
     * GradingTask(Q1a, Q1aTester.java, Q1, Q1a.java)
     * GradingTask(Q1b, Q1bTester.java, Q1, Q1b.java)
     * GradingTask(Q2a, Q2aTester.java, Q2, Q2a.java)
     * GradingTask(Q2b, Q2bTester.java, Q2, Q2b.java)
     * GradingTask(Q3, Q3Tester.java, Q3, Q3.java)
     * [ShapeComparator.java skipped - no tester]
     * * @param structure ExamStructure discovered from template
     * @param testerMap TesterMap discovered from testers directory
     * @return GradingPlan containing all matched grading tasks
     */
    public GradingPlan buildPlan(ExamStructure structure, TesterMap testerMap) {
        List<GradingTask> tasks = new ArrayList<>();
        Map<String, List<String>> questionFiles = structure.getQuestionFiles();

        // GPB-2 fix: track question IDs that already have a task to prevent duplicates.
        // Without this, both Q1a.java AND Q1a.class would each match Q1aTester and create
        // two GradingTask objects - causing ExecutionController to grade Q1a twice per student.
        // Priority: .java is preferred over .class (processed first due to alphabetical sort).
        Set<String> assignedTaskIds = new HashSet<>();

        System.out.println("   🔍 Finalizing Grading Plan...");

        for (String questionFolder : questionFiles.keySet()) {
            List<String> filesInFolder = questionFiles.get(questionFolder);
            int tasksBeforeFolder = tasks.size(); // snapshot before processing this folder

            for (String fileName : filesInFolder) {
                // 1. Silently skip system junk
                if (fileName.equalsIgnoreCase(".DS_Store") || fileName.contains("__MACOSX")) continue;

                // 2. Identify Data Files (.txt, .csv, etc.)
                if (!fileName.toLowerCase().endsWith(".java") && !fileName.toLowerCase().endsWith(".class")) {
                    System.out.println("      ℹ️  [DATA FILE] " + fileName + ": Required dependency for " + questionFolder + ".");
                    continue;
                }

                // 3. Extract clean question ID from filename.
                //    resolveTemplateId() handles both flat ("Q1a.java") and nested
                //    ("src/Q1a.java") paths consistently in one place. [Fix-9: DRY]
                String templateName = resolveTemplateId(fileName);

                // 4. O(1) tester lookup via pre-normalised lowercase key. [Fix-2]
                //    TesterMap now stores all keys in lowercase so direct get() works.
                String matchedTester = testerMap.getTesterMapping().get(templateName.toLowerCase());

                // 5. Categorize based on match result
                if (matchedTester != null) {
                    // GPB-2 fix: check if this question ID already has a task assigned.
                    // This prevents both Q1a.java and Q1a.class from creating duplicate tasks.
                    if (assignedTaskIds.contains(templateName.toLowerCase())) {
                        System.out.println("      ⚠️  [DUPLICATE] " + fileName + ": Task for '" + templateName + "' already assigned (preferring .java over .class). Skipping.");
                        continue;
                    }
                    // Use the template's exact casing for the Task ID
                    tasks.add(new GradingTask(templateName, matchedTester, questionFolder, fileName));
                    assignedTaskIds.add(templateName.toLowerCase());
                    System.out.println("      ✅ [TASK] " + templateName + ": Matched with answer key " + matchedTester + ".");
                } else {
                    System.out.println("      💡 [HELPER] " + templateName + ": Supporting code found. Used for compilation only.");
                }
            }

            // NEW-E fix: warn if an entire Q folder produced 0 gradable tasks.
            // Without this, a missing Q4Tester shows individual [HELPER] lines per file
            // but no folder-level signal that the whole question was skipped.
            // This helps instructors quickly spot a missing tester for an entire question.
            int tasksFromFolder = tasks.size() - tasksBeforeFolder;
            boolean folderHadGradableFiles = filesInFolder.stream()
                .anyMatch(f -> f.toLowerCase().endsWith(".java") || f.toLowerCase().endsWith(".class"));
            if (folderHadGradableFiles && tasksFromFolder == 0) {
                System.out.println("      ⚠️  [WARNING] " + questionFolder + ": Found gradable files but no matching tester. "
                    + "Entire folder skipped. Ensure a tester like '"
                    + questionFolder + "Tester.java' exists in the testers directory.");
            }
        }
        
        // GPB-1 fix: warn clearly if no tasks were matched instead of silently
        // returning an empty plan. Without this, the program runs to completion
        // grading nobody and showing no results - very hard to diagnose.
        if (tasks.isEmpty()) {
            System.out.println("   ⚠️  WARNING: 0 gradable tasks could be mapped!");
            System.out.println("      Check that tester filenames match template filenames.");
            System.out.println("      Example: Q1a.java in template needs Q1aTester.java in testers/");
        }

        System.out.println("   🏁 Successfully mapped " + tasks.size() + " gradable tasks.");
        return new GradingPlan(tasks);
    }
    
    /**
     * Validates that exam structure and tester map are compatible
     * 
     * CHECKS:
     * - At least one question file exists
     * - At least one tester exists
     * - At least one match possible
     * 
     * CONSISTENCY NOTE:
     * - Uses the same inline dot-strip + case-insensitive lookup logic as buildPlan()
     * - Previously called a private extractQuestionId() with different logic, which
     *   could cause isCompatible() and buildPlan() to disagree on matches. Removed.
     * 
     * @param structure ExamStructure to validate
     * @param testerMap TesterMap to validate
     * @return true if compatible, false otherwise
     */
    public boolean isCompatible(ExamStructure structure, TesterMap testerMap) {

        if (structure.getQuestionFiles().isEmpty()) return false;
        if (testerMap.getTesterMapping().isEmpty()) return false;

        // Uses resolveTemplateId() + O(1) lowercase get() — identical logic to buildPlan().
        // [Fix-1: stale T-27 bug in old isCompatible] [Fix-9: DRY] [Fix-2: O(1) lookup]
        for (List<String> files : structure.getQuestionFiles().values()) {
            for (String file : files) {
                if (file.equalsIgnoreCase(".DS_Store") || file.contains("__MACOSX")) continue;
                String fileLower = file.toLowerCase();
                if (!fileLower.endsWith(".java") && !fileLower.endsWith(".class")) continue;

                String templateName = resolveTemplateId(file);
                if (testerMap.getTesterMapping().containsKey(templateName.toLowerCase())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Extracts a clean question ID from a filename, handling both flat and nested paths.
     *
     * EXAMPLES:
     *   "Q1a.java"      → "Q1a"   (flat, direct in Q folder)
     *   "src/Q1a.java"  → "Q1a"   (nested, T-27 fix)
     *   "Q1a.class"     → "Q1a"
     *   "Q1a"           → "Q1a"   (no extension)
     *
     * Used by both buildPlan() and isCompatible() to guarantee they always
     * agree on what constitutes a valid match. [Fix-9: DRY, Fix-1: consistency]
     *
     * @param fileName raw filename or relative path from ExamStructure file list
     * @return base name without extension and without any leading path segments
     */
    private String resolveTemplateId(String fileName) {
        // Strip leading path segments (e.g. "src/Q1a.java" → "Q1a.java")
        String baseName = fileName.contains("/")
            ? fileName.substring(fileName.lastIndexOf('/') + 1)
            : fileName;
        // Strip extension
        int dotIndex = baseName.lastIndexOf('.');
        return (dotIndex == -1) ? baseName : baseName.substring(0, dotIndex);
    }
}