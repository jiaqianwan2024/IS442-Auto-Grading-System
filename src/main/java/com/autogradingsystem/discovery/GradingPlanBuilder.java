package com.autogradingsystem.discovery;

import com.autogradingsystem.model.*;

import java.util.ArrayList;
import java.util.List;

/**
 * GradingPlanBuilder - Combines Structure and Testers into Grading Plan
 * 
 * PURPOSE:
 * - Takes ExamStructure (what questions exist)
 * - Takes TesterMap (what testers we have)
 * - Matches them together intelligently
 * - Creates complete GradingPlan ready for execution
 * 
 * WHY WE NEED THIS:
 * - Eliminates hardcoded grading task arrays in ExecutionController
 * - Handles missing testers gracefully (logs warning, continues)
 * - Creates task for each question-tester pair automatically
 * 
 * MATCHING STRATEGY:
 * - For each question file (Q1a.java, Q1b.java, etc.):
 *   1. Extract question ID: "Q1a.java" → "Q1a"
 *   2. Look for matching tester: "Q1a" → "Q1aTester.java"
 *   3. If found: Create GradingTask with tester
 *   4. If not found: Create GradingTask with null tester (will score 0)
 * 
 * GRACEFUL DEGRADATION:
 * - Missing tester? Logs warning but continues ⚠️
 * - Extra tester? Ignored (no question for it)
 * - Never crashes - always returns valid plan
 * 
 * EXAMPLE:
 * Input ExamStructure:
 *   Q1: [Q1a.java, Q1b.java]
 *   Q2: [Q2a.java, Q2b.java]
 *   Q3: [Q3.java]
 * 
 * Input TesterMap:
 *   Q1a → Q1aTester.java
 *   Q1b → Q1bTester.java
 *   Q2a → Q2aTester.java
 *   Q2b → Q2bTester.java
 *   Q3  → Q3Tester.java
 * 
 * Output GradingPlan:
 *   [
 *     Task(Q1a, Q1aTester.java, Q1, Q1a.java),
 *     Task(Q1b, Q1bTester.java, Q1, Q1b.java),
 *     Task(Q2a, Q2aTester.java, Q2, Q2a.java),
 *     Task(Q2b, Q2bTester.java, Q2, Q2b.java),
 *     Task(Q3, Q3Tester.java, Q3, Q3.java)
 *   ]
 * 
 * @author IS442 Team
 * @version 1.0
 */
public class GradingPlanBuilder {
    
    /**
     * Builds a complete grading plan from exam structure and testers.
     * 
     * WORKFLOW:
     * 1. Initialize empty task list
     * 2. For each question in structure:
     *    a. Get question folder (Q1, Q2, Q3)
     *    b. Get files for that question (Q1a.java, Q1b.java)
     *    c. For each file:
     *       - Extract question ID from filename
     *       - Look up tester in TesterMap
     *       - Create GradingTask
     *       - Add to task list
     * 3. Create GradingPlan from task list
     * 4. Return plan
     * 
     * ERROR HANDLING:
     * - Missing tester: Logs warning, creates task with null tester
     * - Empty structure: Returns empty plan (valid but unusual)
     * - Null inputs: Throws IllegalArgumentException
     * 
     * @param structure ExamStructure from TemplateDiscovery
     * @param testers TesterMap from TesterDiscovery
     * @return GradingPlan ready for execution
     * @throws IllegalArgumentException if structure or testers is null
     */
    public GradingPlan buildPlan(ExamStructure structure, TesterMap testers) {
        
        // Input validation
        if (structure == null) {
            throw new IllegalArgumentException("ExamStructure cannot be null");
        }
        if (testers == null) {
            throw new IllegalArgumentException("TesterMap cannot be null");
        }
        
        System.out.println("\n📋 Building grading plan...");
        System.out.println("   Matching testers to questions...");
        
        // STEP 1: Initialize task list
        List<GradingTask> tasks = new ArrayList<>();
        
        // Counters for summary
        int matchedCount = 0;
        int missingCount = 0;
        
        // STEP 2: Iterate through all questions in structure
        // Questions are already sorted (Q1, Q2, Q3, ...)
        for (String questionFolder : structure.getQuestions()) {
            
            // Get all files for this question
            // e.g., for Q1: [Q1a.java, Q1b.java]
            List<String> files = structure.getQuestionFiles(questionFolder);
            
            // STEP 2a: Process each file in the question folder
            for (String studentFile : files) {
                
                // STEP 2b: Extract question ID from filename
                // "Q1a.java" → "Q1a"
                String questionId = extractQuestionId(studentFile);
                
                if (questionId == null) {
                    // Couldn't extract question ID - skip this file
                    System.err.println("   ⚠️  Could not extract question ID from: " + studentFile);
                    continue;
                }
                
                // STEP 2c: Look up tester for this question
                String testerFile = testers.getTester(questionId);
                
                // STEP 2d: Create GradingTask
                GradingTask task = new GradingTask(
                    questionId,      // e.g., "Q1a"
                    testerFile,      // e.g., "Q1aTester.java" (or null if not found)
                    questionFolder,  // e.g., "Q1"
                    studentFile      // e.g., "Q1a.java"
                );
                
                tasks.add(task);
                
                // STEP 2e: Log result
                if (testerFile != null) {
                    System.out.println("   ✅ " + questionId + ": " + testerFile);
                    matchedCount++;
                } else {
                    System.err.println("   ❌ " + questionId + ": NO TESTER FOUND (will score 0)");
                    missingCount++;
                }
            }
        }
        
        // STEP 3: Log summary
        System.out.println("\n   📊 Grading plan complete:");
        System.out.println("      Total tasks: " + tasks.size());
        System.out.println("      Matched: " + matchedCount);
        if (missingCount > 0) {
            System.err.println("      ⚠️  Missing testers: " + missingCount);
        }
        
        // STEP 4: Create and return GradingPlan
        GradingPlan plan = new GradingPlan(tasks);
        return plan;
    }
    
    /**
     * Extracts question ID from Java filename.
     * 
     * CONVENTION:
     * - Java files are named: [QuestionID].java
     * - Remove ".java" extension to get question ID
     * 
     * EXAMPLES:
     * - "Q1a.java" → "Q1a" ✅
     * - "Q2b.java" → "Q2b" ✅
     * - "Q3.java"  → "Q3" ✅
     * - "Q10a.java" → "Q10a" ✅
     * - "Helper.java" → "Helper" ⚠️ (valid but unexpected)
     * - "NotJava.txt" → null ❌ (doesn't end with .java)
     * 
     * VALIDATION:
     * - Checks that filename ends with ".java"
     * - Checks that there's something before ".java"
     * - Returns null if validation fails
     * 
     * WHY SIMPLE STRING MANIPULATION:
     * - Easy to understand
     * - Fast execution
     * - No complex regex needed
     * - Maintainable
     * 
     * @param filename Java filename (e.g., "Q1a.java")
     * @return Question ID (e.g., "Q1a") or null if invalid format
     */
    private String extractQuestionId(String filename) {
        
        // Check if filename ends with ".java"
        if (!filename.endsWith(".java")) {
            return null;  // Not a Java file
        }
        
        // Remove ".java" extension
        // "Q1a.java" → "Q1a"
        // Length of ".java" is 5 characters
        String questionId = filename.substring(0, filename.length() - ".java".length());
        
        // Validate that we have something left
        // If filename was just ".java", questionId would be empty
        if (questionId.isEmpty()) {
            return null;  // Invalid - no question ID
        }
        
        // Additional validation: Question ID should match expected pattern
        // This is optional but helps catch mistakes
        if (!questionId.matches("^Q\\d+[a-z]?$")) {
            // Pattern: Q + digits + optional lowercase letter
            // Q1 ✅, Q1a ✅, Q10b ✅
            // Helper ❌, Q ❌, Qa ❌
            System.err.println("   ⚠️  Warning: '" + questionId + "' doesn't match expected pattern (Q + number + optional letter)");
            // Still return it - maybe it's a valid edge case
        }
        
        return questionId;
    }
    
    /**
     * Validates that a grading plan is reasonable.
     * Used for testing and debugging.
     * 
     * CHECKS:
     * - Plan is not null
     * - Plan has at least one task
     * - All tasks have valid question IDs
     * 
     * @param plan GradingPlan to validate
     * @return true if plan is valid, false otherwise
     */
    public boolean validatePlan(GradingPlan plan) {
        
        if (plan == null) {
            System.err.println("❌ Validation failed: Plan is null");
            return false;
        }
        
        if (plan.getTasks().isEmpty()) {
            System.err.println("⚠️  Warning: Plan has no tasks");
            return false;
        }
        
        // Check each task has a question ID
        for (GradingTask task : plan.getTasks()) {
            if (task.getQuestionId() == null || task.getQuestionId().isEmpty()) {
                System.err.println("❌ Validation failed: Task has no question ID");
                return false;
            }
        }
        
        return true;
    }
}