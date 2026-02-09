package com.autogradingsystem.util;

import com.autogradingsystem.model.GradingResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * ScoreAnalyzer - Utility for Analyzing and Normalizing Grading Results
 * 
 * PURPOSE:
 * - Infers maximum scores for each question from actual results
 * - Updates results with max scores
 * - Calculates totals and statistics
 * 
 * WHY NEEDED:
 * - Different questions have different max scores (Q1a=3.0, Q3=4.0, etc.)
 * - We don't know max scores until we see all student results
 * - Highest score achieved = likely the max score
 * 
 * @author IS442 Team
 * @version 1.0
 */
public class ScoreAnalyzer {
    
    /**
     * Infers maximum score for each question from results
     * 
     * FIX 3: Now parses tester files when all students score 0
     * 
     * STRATEGY:
     * - Group results by question ID
     * - Find highest score achieved for each question
     * - If all students got 0, parse tester file to count tests
     * 
     * EXAMPLE:
     * Q1a results: [3.0, 2.0, 3.0, 1.0, 3.0] → max = 3.0 (from results)
     * Q3 results: [0.0, 0.0, 0.0, 0.0, 0.0] → max = 4.0 (from tester file)
     * 
     * @param results List of all grading results
     * @return Map of question ID → max score
     */
    public static Map<String, Double> inferMaxScores(List<GradingResult> results) {
        
        Map<String, Double> maxScores = new HashMap<>();
        
        // Group results by question ID
        Map<String, List<GradingResult>> byQuestion = results.stream()
            .collect(Collectors.groupingBy(GradingResult::getQuestionId));
        
        // Find max score for each question
        byQuestion.forEach((questionId, questionResults) -> {
            
            // STEP 1: Try to infer from results
            double maxFromResults = questionResults.stream()
                .mapToDouble(GradingResult::getScore)
                .max()
                .orElse(0.0);
            
            // STEP 2: If all students got 0, parse tester file
            if (maxFromResults == 0.0) {
                try {
                    double maxFromTester = getMaxScoreFromTester(questionId);
                    
                    if (maxFromTester > 0.0) {
                        System.out.println("   ℹ️  " + questionId + 
                            ": No student scored points, max inferred from tester = " + 
                            maxFromTester);
                        maxScores.put(questionId, maxFromTester);
                        return;
                    }
                    
                } catch (Exception e) {
                    // Couldn't read tester, fall back to 0.0
                }
            }
            
            maxScores.put(questionId, maxFromResults);
        });
        
        return maxScores;
    }
    
    /**
     * FIX 3: Parses tester file to determine true max score
     * 
     * Counts occurrences of "score +=" to determine number of tests
     * Each test typically awards 1 point
     * 
     * @param questionId Question ID (e.g., "Q1a", "Q2b")
     * @return Max score (number of tests)
     */
    private static double getMaxScoreFromTester(String questionId) throws IOException {
        
        // Build path to tester file
        Path testerPath = Paths.get("src", "main", "resources", "testers", 
                                   questionId + "Tester.java");
        
        if (!Files.exists(testerPath)) {
            return 0.0;  // Tester not found
        }
        
        String content = Files.readString(testerPath);
        
        // Count "score +=" occurrences
        // Matches: "score +=", "score += 1", "score+="
        Pattern pattern = Pattern.compile("score\\s*\\+");
        Matcher matcher = pattern.matcher(content);
        
        int testCount = 0;
        while (matcher.find()) {
            testCount++;
        }
        
        return (double) testCount;
    }
    
    /**
     * Updates all results with inferred max scores
     * 
     * WORKFLOW:
     * 1. Infer max scores from results
     * 2. Create new results with max scores set
     * 3. Return updated list
     * 
     * @param results Original results (without max scores)
     * @return Updated results (with max scores)
     */
    public static List<GradingResult> updateWithMaxScores(List<GradingResult> results) {
        
        // Infer max scores
        Map<String, Double> maxScores = inferMaxScores(results);
        
        // Update each result
        List<GradingResult> updated = new ArrayList<>();
        for (GradingResult result : results) {
            String questionId = result.getQuestionId();
            double maxScore = maxScores.getOrDefault(questionId, 0.0);
            
            // Create new result with max score
            updated.add(result.withMaxScore(maxScore));
        }
        
        return updated;
    }
    
    /**
     * Calculates total score for a student
     * 
     * @param studentResults All results for one student
     * @return Total points earned
     */
    public static double calculateTotalScore(List<GradingResult> studentResults) {
        return studentResults.stream()
            .mapToDouble(GradingResult::getScore)
            .sum();
    }
    
    /**
     * Calculates total max score for a student
     * 
     * @param studentResults All results for one student
     * @return Total max possible points
     */
    public static double calculateTotalMaxScore(List<GradingResult> studentResults) {
        return studentResults.stream()
            .mapToDouble(GradingResult::getMaxScore)
            .sum();
    }
    
    /**
     * Calculates percentage for a student
     * 
     * @param studentResults All results for one student
     * @return Percentage (0-100)
     */
    public static double calculatePercentage(List<GradingResult> studentResults) {
        double total = calculateTotalScore(studentResults);
        double maxTotal = calculateTotalMaxScore(studentResults);
        
        if (maxTotal == 0.0) {
            return 0.0;
        }
        
        return (total / maxTotal) * 100.0;
    }
    
    /**
     * Groups results by student
     * 
     * @param results All grading results
     * @return Map of student username → their results
     */
    public static Map<String, List<GradingResult>> groupByStudent(List<GradingResult> results) {
        return results.stream()
            .collect(Collectors.groupingBy(GradingResult::getStudentUsername));
    }
    
    /**
     * Groups results by question
     * 
     * @param results All grading results
     * @return Map of question ID → results for that question
     */
    public static Map<String, List<GradingResult>> groupByQuestion(List<GradingResult> results) {
        return results.stream()
            .collect(Collectors.groupingBy(GradingResult::getQuestionId));
    }
}