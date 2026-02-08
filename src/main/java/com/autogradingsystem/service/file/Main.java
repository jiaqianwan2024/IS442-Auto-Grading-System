package com.autogradingsystem.service.file;

import java.io.IOException;

import com.autogradingsystem.controller.ExecutionController;
import com.autogradingsystem.model.GradingPlan;

/**
 * Main - Entry Point for Auto-Grading System
 * 
 * PHASE 2 VERSION:
 * - Runs Phase 1: Extraction & Validation
 * - Runs Phase 2: Discovery & Planning
 * - Phase 3: Grading (will be added later)
 * 
 * @author IS442 Team
 * @version 2.0 (Phase 2)
 */
public class Main {
    
    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════════════╗");
        System.out.println("║              IS442 AUTO-GRADING SYSTEM - PHASE 2 TEST              ║");
        System.out.println("╚════════════════════════════════════════════════════════════════════╝");
        
        try {
            // ================================================================
            // INITIALIZE CONTROLLER
            // ================================================================
            
            ExecutionController controller = new ExecutionController();
            
            // ================================================================
            // PHASE 1 + PHASE 2: INITIALIZE
            // ================================================================
            
            GradingPlan plan = controller.initialize();
            
            // ================================================================
            // DISPLAY RESULTS
            // ================================================================
            
            System.out.println("✅ Initialization complete!");
            System.out.println("   Grading plan: " + plan.getSummary());
            
            // Display detailed task breakdown
            System.out.println("\n" + "─".repeat(70));
            System.out.println("📋 TASK BREAKDOWN");
            System.out.println("─".repeat(70));
            
            int taskNum = 1;
            for (var task : plan.getTasks()) {
                String status = task.hasTester() ? "✅" : "❌";
                String tester = task.hasTester() ? task.getTesterFile() : "MISSING";
                
                System.out.printf("%s [%d] %-6s | Folder: %-4s | File: %-12s | Tester: %s%n",
                    status,
                    taskNum++,
                    task.getQuestionId(),
                    task.getStudentFolder(),
                    task.getStudentFile(),
                    tester
                );
            }
            
            System.out.println("─".repeat(70));
            
            // Warnings if any
            if (plan.getUngradableTaskCount() > 0) {
                System.err.println("\n⚠️  WARNING: " + plan.getUngradableTaskCount() + 
                                 " task(s) missing testers");
                System.err.println("These tasks will score 0 during grading:");
                for (var task : plan.getTasksWithoutTesters()) {
                    System.err.println("  • " + task.getQuestionId() + 
                                     " (expected: " + task.getQuestionId() + "Tester.java)");
                }
            }
            
            // ================================================================
            // PHASE 3: GRADING (Not implemented yet)
            // ================================================================
            
            // Uncomment when Phase 3 is ready:
            // System.out.println("\n=== PHASE 3: GRADING ===");
            // controller.runGrading(plan);
            
            // ================================================================
            // COMPLETION
            // ================================================================
            
            System.out.println("\n" + "=".repeat(70));
            System.out.println("✅ PHASE 2 TEST COMPLETE");
            System.out.println("=".repeat(70));
            
            if (plan.getGradableTaskCount() == plan.getTaskCount()) {
                System.out.println("🎉 All tasks have testers - ready for Phase 3!");
            } else {
                System.out.println("⚠️  Some tasks missing testers - add them before Phase 3");
            }
            
            System.out.println("\n📝 Next Steps:");
            System.out.println("1. ✅ Phase 1 complete - Students extracted");
            System.out.println("2. ✅ Phase 2 complete - Grading plan built");
            System.out.println("3. ⏳ Phase 3 pending - Implement grading execution");
            System.out.println("4. ⏳ Phase 4 pending - Implement report generation");
            
            System.out.println("\n💡 Verify:");
            System.out.println("- Check extracted students in: data/extracted/");
            System.out.println("- Verify grading plan matches your exam structure");
            System.out.println("- Ensure all testers are present");
            
        } catch (IOException e) {
            // ================================================================
            // ERROR HANDLING
            // ================================================================
            
            System.err.println("\n" + "=".repeat(70));
            System.err.println("❌ ERROR DURING INITIALIZATION");
            System.err.println("=".repeat(70));
            System.err.println("Error: " + e.getMessage());
            
            System.err.println("\n📋 Common Issues:");
            System.err.println("1. Missing CSV: config/IS442-ScoreSheet.csv");
            System.err.println("2. Missing student ZIP: data/input/submissions/student-submission.zip");
            System.err.println("3. Missing template ZIP: data/input/template/RenameToYourUsername.zip");
            System.err.println("4. Missing testers: src/main/resources/testers/*.java");
            System.err.println("5. Wrong folder name: data/input/submission (should be submissions)");
            
            System.err.println("\n📚 Stack Trace:");
            e.printStackTrace();
            
            System.exit(1);
            
        } catch (Exception e) {
            // Catch any other unexpected errors
            System.err.println("\n❌ UNEXPECTED ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}