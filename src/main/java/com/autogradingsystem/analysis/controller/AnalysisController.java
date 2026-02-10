package com.autogradingsystem.analysis.controller;

import com.autogradingsystem.analysis.service.ScoreAnalyzer;
import com.autogradingsystem.model.GradingResult;

import java.util.List;
import java.util.Map;

/**
 * AnalysisController - Brain for Analysis Service
 * 
 * PURPOSE:
 * - Coordinates results analysis and display
 * - Acts as entry point for analysis service
 * - Called by Main.java after grading complete
 * 
 * RESPONSIBILITIES:
 * - Infer maximum scores for each question
 * - Update results with max scores
 * - Group results by student
 * - Display compact results view
 * - Display detailed statistics
 * 
 * @author IS442 Team
 * @version 4.0
 */
public class AnalysisController {
    
    /**
     * Analyzes grading results and displays formatted output
     * 
     * WORKFLOW:
     * 1. Infer max scores (from results or testers)
     * 2. Update all results with max scores
     * 3. Display max scores per question
     * 4. Display detailed question statistics
     * 5. Display compact view (one line per student)
     * 6. Display overall statistics
     * 
     * @param results List of all GradingResult objects
     */
    public void analyzeAndDisplay(List<GradingResult> results) {
        
        if (results.isEmpty()) {
            System.out.println("\n⚠️  No results to analyze");
            return;
        }
        
        // Infer max scores
        Map<String, Double> maxScores = ScoreAnalyzer.inferMaxScores(results);
        
        // Update results with max scores
        List<GradingResult> updatedResults = ScoreAnalyzer.updateWithMaxScores(results);
        
        // Display max scores
        displayMaxScores(maxScores);
        
        // Display detailed question statistics
        displayQuestionStatistics(updatedResults);
        
        // Group by student and display
        Map<String, List<GradingResult>> byStudent = ScoreAnalyzer.groupByStudent(updatedResults);
        displayCompactView(byStudent);
        
        // Display overall statistics
        displayOverallStatistics(byStudent);
    }
    
    /**
     * Displays detected max scores for each question
     * 
     * @param maxScores Map of question ID to max score
     */
    private void displayMaxScores(Map<String, Double> maxScores) {
        
        System.out.println("\n📋 Detected Max Scores per Question:");
        
        maxScores.forEach((questionId, maxScore) -> {
            System.out.println("   " + questionId + ": " + maxScore + " points");
        });
        
        System.out.println();
    }
    
    /**
     * Displays compact view of all results (one line per student)
     * 
     * FORMAT: student: Q1a:X Q1b:Y ... Total: Z/W
     * 
     * @param resultsByStudent Map of student username to their results
     */
    private void displayCompactView(Map<String, List<GradingResult>> resultsByStudent) {
        
        System.out.println("=".repeat(70));
        System.out.println("📋 GRADING RESULTS - COMPACT VIEW");
        System.out.println("=".repeat(70));
        System.out.println();
        
        // Find longest username for alignment
        int maxUsernameLength = resultsByStudent.keySet().stream()
            .mapToInt(String::length)
            .max()
            .orElse(20);
        
        // Display each student's results
        resultsByStudent.forEach((username, studentResults) -> {
            
            // Build result line
            StringBuilder line = new StringBuilder();
            
            // Student name (padded for alignment)
            line.append(String.format("%-" + maxUsernameLength + "s", username));
            line.append(":  ");
            
            // Individual question scores
            for (GradingResult result : studentResults) {
                line.append(result.getQuestionId()).append(":");
                line.append(result.getScore()).append("  ");
            }
            
            // Calculate total
            double total = ScoreAnalyzer.calculateTotalScore(studentResults);
            double maxTotal = ScoreAnalyzer.calculateTotalMaxScore(studentResults);
            
            // Add total
            line.append(" Total: ").append(total).append("/").append(maxTotal);
            
            System.out.println(line.toString());
        });
        
        System.out.println();
        System.out.println("=".repeat(70));
    }
    
    /**
     * Displays detailed statistics for each question
     * 
     * SHOWS:
     * - Average score
     * - Number of perfect scores
     * - Number of partial credit
     * - Number of failures
     * 
     * @param results List of all updated results
     */
    private void displayQuestionStatistics(List<GradingResult> results) {
        
        System.out.println("\n" + "=".repeat(70));
        System.out.println("📈 QUESTION-BY-QUESTION STATISTICS");
        System.out.println("=".repeat(70));
        System.out.println();
        
        // Group by question
        Map<String, List<GradingResult>> byQuestion = ScoreAnalyzer.groupByQuestion(results);
        
        // Display stats for each question
        byQuestion.forEach((questionId, questionResults) -> {
            
            if (questionResults.isEmpty()) return;
            
            // Get max score
            double maxScore = questionResults.get(0).getMaxScore();
            
            // Calculate statistics
            double average = ScoreAnalyzer.calculateAverageScore(questionResults);
            long perfect = ScoreAnalyzer.countPerfect(questionResults);
            long passed = ScoreAnalyzer.countPassed(questionResults);
            long failed = ScoreAnalyzer.countFailed(questionResults);
            long partial = passed - perfect; // passed but not perfect
            
            int totalStudents = questionResults.size();
            double avgPercentage = maxScore > 0 ? (average / maxScore) * 100 : 0;
            
            // Display question header
            System.out.println(questionId + " (" + maxScore + " points):");
            
            // Display average
            System.out.printf("  Average: %.1f / %.1f (%.1f%%)%n", 
                average, maxScore, avgPercentage);
            
            // Display distribution
            System.out.printf("  Perfect scores: %d/%d students%n", perfect, totalStudents);
            if (partial > 0) {
                System.out.printf("  Partial credit: %d/%d students%n", partial, totalStudents);
            }
            if (failed > 0) {
                System.out.printf("  Failed: %d/%d students%n", failed, totalStudents);
            }
            
            System.out.println();
        });
    }
    
    /**
     * Displays overall class statistics
     * 
     * SHOWS:
     * - Class average
     * - Highest score (with student)
     * - Lowest score (with student)
     * - Pass rate
     * 
     * @param resultsByStudent Map of student username to their results
     */
    private void displayOverallStatistics(Map<String, List<GradingResult>> resultsByStudent) {
        
        System.out.println("\n" + "=".repeat(70));
        System.out.println("📊 OVERALL CLASS STATISTICS");
        System.out.println("=".repeat(70));
        System.out.println();
        
        if (resultsByStudent.isEmpty()) {
            System.out.println("No data available");
            return;
        }
        
        // Calculate total scores for each student
        double totalSum = 0;
        double maxTotal = 0;
        double highestScore = -1;
        double lowestScore = Double.MAX_VALUE;
        String highestStudent = "";
        String lowestStudent = "";
        int passCount = 0;
        
        for (Map.Entry<String, List<GradingResult>> entry : resultsByStudent.entrySet()) {
            String username = entry.getKey();
            List<GradingResult> studentResults = entry.getValue();
            
            double studentTotal = ScoreAnalyzer.calculateTotalScore(studentResults);
            double studentMax = ScoreAnalyzer.calculateTotalMaxScore(studentResults);
            
            totalSum += studentTotal;
            maxTotal = studentMax; // Same for all students
            
            // Track highest
            if (studentTotal > highestScore) {
                highestScore = studentTotal;
                highestStudent = username;
            }
            
            // Track lowest
            if (studentTotal < lowestScore) {
                lowestScore = studentTotal;
                lowestStudent = username;
            }
            
            // Count passes (>= 50%)
            if (studentMax > 0 && (studentTotal / studentMax) >= 0.5) {
                passCount++;
            }
        }
        
        // Calculate class average
        int studentCount = resultsByStudent.size();
        double classAverage = totalSum / studentCount;
        double classAveragePercentage = maxTotal > 0 ? (classAverage / maxTotal) * 100 : 0;
        
        // Display statistics
        System.out.printf("Class Average: %.1f / %.1f (%.1f%%)%n", 
            classAverage, maxTotal, classAveragePercentage);
        
        System.out.printf("Highest Score: %.1f / %.1f (%s)%n", 
            highestScore, maxTotal, highestStudent);
        
        System.out.printf("Lowest Score: %.1f / %.1f (%s)%n", 
            lowestScore, maxTotal, lowestStudent);
        
        double passRate = (passCount * 100.0) / studentCount;
        System.out.printf("Pass Rate (≥50%%): %d/%d students (%.1f%%)%n", 
            passCount, studentCount, passRate);
        
        System.out.println();
        System.out.println("=".repeat(70));
    }
}