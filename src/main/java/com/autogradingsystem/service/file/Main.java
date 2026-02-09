package com.autogradingsystem.service.file;

import com.autogradingsystem.controller.ExecutionController;
import com.autogradingsystem.model.GradingPlan;
import com.autogradingsystem.model.GradingResult;
import com.autogradingsystem.util.ScoreAnalyzer;

import java.util.*;

/**
 * Main - Entry Point for Auto-Grading System
 * 
 * COMPLETE WORKFLOW:
 * 1. Phase 1: Extract and validate student submissions
 * 2. Phase 2: Discover exam structure and build grading plan
 * 3. Phase 3: Execute grading for all students (RAW POINTS)
 * 4. Infer max scores and display results
 * 5. Phase 4: Generate reports (future)
 * 
 * OUTPUT FORMAT:
 * chee.teo.2022:   Q1a:3.0   Q1b:2.0   Q2a:5.0   Q2b:1.0   Q3:0.0   Total: 11.0/15.0
 * 
 * @author IS442 Team
 * @version 3.1 (Raw Points + Max Score Inference)
 */
public class Main {
    
    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                     IS442 AUTO-GRADING SYSTEM                      ║");
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
            
            System.out.println("✅ Initialization complete!");
            System.out.println("   Grading plan: " + plan.getSummary());
            
            // ================================================================
            // PHASE 3: GRADING EXECUTION
            // ================================================================
            
            List<GradingResult> results = controller.runGrading(plan);
            
            // ================================================================
            // INFER MAX SCORES
            // ================================================================
            
            System.out.println("\n" + "=".repeat(70));
            System.out.println("📊 ANALYZING RESULTS & INFERRING MAX SCORES");
            System.out.println("=".repeat(70));
            
            // Infer max scores from results
            Map<String, Double> maxScores = ScoreAnalyzer.inferMaxScores(results);
            
            System.out.println("\n📋 Detected Max Scores per Question:");
            maxScores.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    System.out.printf("   %-10s : %.1f points%n", entry.getKey(), entry.getValue());
                });
            
            // Update results with max scores
            results = ScoreAnalyzer.updateWithMaxScores(results);
            
            System.out.println("=".repeat(70));
            
            // ================================================================
            // DISPLAY RESULTS IN REQUESTED FORMAT
            // ================================================================
            
            displayResultsCompact(results);
            
            // ================================================================
            // DISPLAY DETAILED SUMMARY
            // ================================================================
            
            displayDetailedSummary(results);
            
            // ================================================================
            // PHASE 4: REPORTING (Future)
            // ================================================================
            
            // TODO: ReportGenerator.generateCSV(results);
            // TODO: ReportGenerator.generateHTML(results);
            
            System.out.println("\n╔════════════════════════════════════════════════════════════════════╗");
            System.out.println("║                    SYSTEM COMPLETE                                 ║");
            System.out.println("╚════════════════════════════════════════════════════════════════════╝");
            
        } catch (Exception e) {
            // ================================================================
            // ERROR HANDLING
            // ================================================================
            
            System.err.println("\n" + "=".repeat(70));
            System.err.println("❌ FATAL ERROR");
            System.err.println("=".repeat(70));
            System.err.println("Error: " + e.getMessage());
            System.err.println("\nStack Trace:");
            e.printStackTrace();
            
            System.exit(1);
        }
    }
    
    /**
     * Display results in compact format (requested format)
     * 
     * FORMAT:
     * chee.teo.2022:   Q1a:3.0   Q1b:2.0   Q2a:5.0   Q2b:1.0   Q3:0.0   Total: 11.0/15.0
     * david.2024:      Q1a:3.0   Q1b:3.0   Q2a:4.0   Q2b:2.0   Q3:4.0   Total: 16.0/20.0
     * 
     * @param results List of all grading results (with max scores)
     */
    private static void displayResultsCompact(List<GradingResult> results) {
        
        System.out.println("\n" + "=".repeat(70));
        System.out.println("📋 GRADING RESULTS - COMPACT VIEW");
        System.out.println("=".repeat(70) + "\n");
        
        // Group by student
        Map<String, List<GradingResult>> byStudent = ScoreAnalyzer.groupByStudent(results);
        
        // Get sorted list of students
        List<String> students = new ArrayList<>(byStudent.keySet());
        Collections.sort(students);
        
        // Get sorted list of question IDs (for consistent ordering)
        Set<String> allQuestions = new TreeSet<>();
        results.forEach(r -> allQuestions.add(r.getQuestionId()));
        
        // Display each student
        for (String studentUsername : students) {
            List<GradingResult> studentResults = byStudent.get(studentUsername);
            
            // Create map for easy lookup
            Map<String, GradingResult> resultMap = new HashMap<>();
            studentResults.forEach(r -> resultMap.put(r.getQuestionId(), r));
            
            // Build output line
            StringBuilder line = new StringBuilder();
            line.append(String.format("%-20s", studentUsername + ":"));
            
            // Add each question score
            for (String questionId : allQuestions) {
                GradingResult result = resultMap.get(questionId);
                if (result != null) {
                    line.append(String.format("   %s:%.1f", questionId, result.getScore()));
                } else {
                    line.append(String.format("   %s:-", questionId));
                }
            }
            
            // Add total
            double total = ScoreAnalyzer.calculateTotalScore(studentResults);
            double maxTotal = ScoreAnalyzer.calculateTotalMaxScore(studentResults);
            line.append(String.format("   Total: %.1f/%.1f", total, maxTotal));
            
            System.out.println(line.toString());
        }
        
        System.out.println();
        System.out.println("=".repeat(70));
    }
    
    /**
     * Display detailed summary of grading results
     * 
     * Shows:
     * - Overall statistics
     * - Per-student breakdown with percentages
     * - Per-question statistics
     * - Error summary
     * 
     * @param results List of all grading results
     */
    private static void displayDetailedSummary(List<GradingResult> results) {
        
        System.out.println("\n" + "=".repeat(70));
        System.out.println("📊 DETAILED STATISTICS");
        System.out.println("=".repeat(70));
        
        // =====================================================================
        // OVERALL STATISTICS
        // =====================================================================
        
        int totalResults = results.size();
        long successfulCount = results.stream().filter(GradingResult::isSuccessful).count();
        long failedCount = results.stream().filter(r -> !r.isSuccessful()).count();
        long perfectCount = results.stream().filter(GradingResult::isPerfect).count();
        
        double totalScore = results.stream().mapToDouble(GradingResult::getScore).sum();
        double maxPossibleScore = results.stream().mapToDouble(GradingResult::getMaxScore).sum();
        double overallPercentage = maxPossibleScore > 0 ? (totalScore / maxPossibleScore) * 100.0 : 0.0;
        
        System.out.println("\n📈 Overall Statistics:");
        System.out.println("   Total results: " + totalResults);
        System.out.println("   Successful: " + successfulCount + " (" + 
                         String.format("%.1f%%", (successfulCount * 100.0 / totalResults)) + ")");
        System.out.println("   Failed: " + failedCount + " (" + 
                         String.format("%.1f%%", (failedCount * 100.0 / totalResults)) + ")");
        System.out.println("   Perfect scores: " + perfectCount);
        System.out.println("   Overall average: " + String.format("%.1f%%", overallPercentage));
        System.out.println("   Total points: " + String.format("%.1f/%.1f", totalScore, maxPossibleScore));
        
        // =====================================================================
        // PER-STUDENT BREAKDOWN
        // =====================================================================
        
        System.out.println("\n" + "─".repeat(70));
        System.out.println("👥 Per-Student Breakdown:");
        System.out.println("─".repeat(70));
        
        Map<String, List<GradingResult>> resultsByStudent = ScoreAnalyzer.groupByStudent(results);
        
        // Sort students alphabetically
        List<String> students = new ArrayList<>(resultsByStudent.keySet());
        Collections.sort(students);
        
        for (String username : students) {
            List<GradingResult> studentResults = resultsByStudent.get(username);
            
            double studentTotal = ScoreAnalyzer.calculateTotalScore(studentResults);
            double studentMax = ScoreAnalyzer.calculateTotalMaxScore(studentResults);
            double studentPercentage = ScoreAnalyzer.calculatePercentage(studentResults);
            
            System.out.printf("   %-20s | ", username);
            System.out.printf("%.1f/%.1f points | ", studentTotal, studentMax);
            System.out.printf("%.1f%%", studentPercentage);
            
            // Show status indicator
            if (studentPercentage >= 100.0) {
                System.out.println(" 🏆 PERFECT");
            } else if (studentPercentage >= 80.0) {
                System.out.println(" ✅ EXCELLENT");
            } else if (studentPercentage >= 60.0) {
                System.out.println(" ⚠️  GOOD");
            } else if (studentPercentage >= 40.0) {
                System.out.println(" ⚠️  NEEDS IMPROVEMENT");
            } else {
                System.out.println(" ❌ POOR");
            }
        }
        
        // =====================================================================
        // PER-QUESTION STATISTICS
        // =====================================================================
        
        System.out.println("\n" + "─".repeat(70));
        System.out.println("📝 Per-Question Statistics:");
        System.out.println("─".repeat(70));
        
        Map<String, List<GradingResult>> resultsByQuestion = ScoreAnalyzer.groupByQuestion(results);
        
        // Sort questions
        List<String> questions = new ArrayList<>(resultsByQuestion.keySet());
        Collections.sort(questions);
        
        for (String questionId : questions) {
            List<GradingResult> questionResults = resultsByQuestion.get(questionId);
            
            double avgScore = questionResults.stream()
                .mapToDouble(GradingResult::getScore)
                .average()
                .orElse(0.0);
            
            double maxScore = questionResults.get(0).getMaxScore();
            
            long passCount = questionResults.stream()
                .filter(r -> r.getScore() > 0)
                .count();
            
            long perfectCount_q = questionResults.stream()
                .filter(GradingResult::isPerfect)
                .count();
            
            System.out.printf("   %-10s | ", questionId);
            System.out.printf("Max: %.1f | ", maxScore);
            System.out.printf("Avg: %.1f | ", avgScore);
            System.out.printf("Pass: %d/%d | ", passCount, questionResults.size());
            System.out.printf("Perfect: %d%n", perfectCount_q);
        }
        
        // =====================================================================
        // ERROR SUMMARY
        // =====================================================================
        
        long compilationErrors = results.stream()
            .filter(r -> r.getStatus().equals("COMPILATION_FAILED"))
            .count();
        
        long fileNotFound = results.stream()
            .filter(r -> r.getStatus().equals("FILE_NOT_FOUND"))
            .count();
        
        long timeouts = results.stream()
            .filter(r -> r.getStatus().equals("TIMEOUT"))
            .count();
        
        if (compilationErrors > 0 || fileNotFound > 0 || timeouts > 0) {
            System.out.println("\n" + "─".repeat(70));
            System.out.println("⚠️  Error Summary:");
            System.out.println("─".repeat(70));
            
            if (compilationErrors > 0) {
                System.out.println("   Compilation errors: " + compilationErrors);
            }
            if (fileNotFound > 0) {
                System.out.println("   Files not found: " + fileNotFound);
            }
            if (timeouts > 0) {
                System.out.println("   Timeouts: " + timeouts);
            }
        }
        
        System.out.println("=".repeat(70));
    }
}