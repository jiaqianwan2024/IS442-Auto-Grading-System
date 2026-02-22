package com.autogradingsystem.discovery.service;

import com.autogradingsystem.discovery.model.ExamStructure;
import com.autogradingsystem.discovery.model.TesterMap;
import com.autogradingsystem.model.GradingTask;
import com.autogradingsystem.model.GradingPlan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

        System.out.println("   🔍 Finalizing Grading Plan...");

        for (String questionFolder : questionFiles.keySet()) {
            List<String> filesInFolder = questionFiles.get(questionFolder);

            for (String fileName : filesInFolder) {
                // 1. Silently skip system junk
                if (fileName.equalsIgnoreCase(".DS_Store") || fileName.contains("__MACOSX")) continue;

                // 2. Identify Data Files (.txt, .csv, etc.)
                if (!fileName.toLowerCase().endsWith(".java") && !fileName.toLowerCase().endsWith(".class")) {
                    System.out.println("      ℹ️  [DATA FILE] " + fileName + ": Required dependency for " + questionFolder + ".");
                    continue;
                }

                // 3. Extract the name exactly as written in the template (e.g., "Q1a")
                int dotIndex = fileName.lastIndexOf('.');
                String templateName = (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);

                // 4. DYNAMIC MATCHING: Find the tester in the map (Case-Insensitive)
                String matchedTester = null;
                for (String registeredId : testerMap.getTesterMapping().keySet()) {
                    if (registeredId.equalsIgnoreCase(templateName)) {
                        matchedTester = testerMap.getTesterMapping().get(registeredId);
                        break;
                    }
                }

                // 5. Categorize based on match result
                if (matchedTester != null) {
                    // Use the template's exact casing for the Task ID
                    tasks.add(new GradingTask(templateName, matchedTester, questionFolder, fileName));
                    System.out.println("      ✅ [TASK] " + templateName + ": Matched with answer key " + matchedTester + ".");
                } else {
                    System.out.println("      💡 [HELPER] " + templateName + ": Supporting code found. Used for compilation only.");
                }
            }
        }
        
        System.out.println("   🏁 Successfully mapped " + tasks.size() + " gradable tasks.");
        return new GradingPlan(tasks);
    }
    
    /**
     * Extracts question ID from Java filename
     * * LOGIC:
     * - Remove ".java" extension
     * - What's left is the question ID
     * * EXAMPLES:
     * - Q1a.java → Q1a ✅
     * - Q1b.java → Q1b ✅
     * - Q2a.java → Q2a ✅
     * - Q3.java → Q3 ✅
     * - ShapeComparator.java → ShapeComparator ✅
     * - NotJava.txt → null ❌ (doesn't end with .java)
     * * VALIDATION:
     * - Returns null if filename doesn't end with ".java"
     * - This should never happen (TemplateDiscovery already filters)
     * - But defensive programming is good practice
     * * @param filename Java filename
     * @return Question ID, or null if not a .java file
     */
    private String extractQuestionId(String filename) {
        // 1. Find the position of the last dot
        int lastDot = filename.lastIndexOf('.');
        
        // 2. If no dot is found, it's not a valid file
        if (lastDot == -1) return null;
        
        // 3. Get the raw name (e.g., "Q1a")
        String rawName = filename.substring(0, lastDot);
        
        // 4. FORMATTING LOGIC: Must match TesterDiscovery (Q + lowercase)
        // Checks if the name starts with Q/q and has content after it
        if (rawName.length() > 1 && Character.toLowerCase(rawName.charAt(0)) == 'q') {
            return "Q" + rawName.substring(1).toLowerCase();
        }
        
        // Fallback for files that don't follow the Qx pattern
        return rawName.toLowerCase();
    }
    
    /**
     * Validates that exam structure and tester map are compatible
     * * CHECKS:
     * - At least one question file exists
     * - At least one tester exists
     * - At least one match possible
     * * This is a pre-check before building plan
     * Helps catch configuration errors early
     * * @param structure ExamStructure to validate
     * @param testerMap TesterMap to validate
     * @return true if compatible, false otherwise
     */
    public boolean isCompatible(ExamStructure structure, TesterMap testerMap) {
        
        // Check structure has questions
        if (structure.getQuestionFiles().isEmpty()) {
            return false;
        }
        
        // Check tester map has testers
        if (testerMap.getTesterMapping().isEmpty()) {
            return false;
        }
        
        // Check if at least one match exists
        for (List<String> files : structure.getQuestionFiles().values()) {
            for (String file : files) {
                String questionId = extractQuestionId(file);
                if (questionId != null && testerMap.hasTester(questionId)) {
                    return true;  // Found at least one match
                }
            }
        }
        
        return false;  // No matches found
    }
}