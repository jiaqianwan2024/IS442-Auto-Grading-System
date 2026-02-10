package com.autogradingsystem.analysis.service;

import com.autogradingsystem.PathConfig;
import com.autogradingsystem.model.GradingResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ScoreAnalyzer - Analyzes Grading Results and Infers Max Scores
 * 
 * PURPOSE:
 * - Infers maximum possible scores for each question
 * - Updates results with inferred max scores
 * - Groups results by student for reporting
 * - Calculates totals and statistics
 * 
 * MAX SCORE INFERENCE (FIX 3):
 * Strategy 1: Use highest score achieved by any student
 * Strategy 2: If all students scored 0, parse tester file to count tests
 * 
 * WHY NEEDED?
 * - We don't hardcode max scores anywhere
 * - System must figure out max scores automatically
 * - Enables flexible exam structures (any number of tests per question)
 * 
 * CHANGES FROM v3.0:
 * - Moved from util/ to analysis.service/
 * - Updated to use PathConfig for tester paths
 * - All methods stay static (utility class pattern)
 * - No logging changes
 * 
 * @author IS442 Team
 * @version 4.0 (Spring Boot Microservices Structure)
 */
public class ScoreAnalyzer {
    
    /**
     * Infers maximum scores for each question
     * 
     * STRATEGY:
     * 1. Group all results by question ID
     * 2. For each question:
     *    a. Find highest score achieved by any student
     *    b. If highest score > 0 → Use that as max score
     *    c. If all students scored 0 → Parse tester file to count tests
     * 3. Return map of question ID → max score
     * 
     * EXAMPLE:
     * Results for Q1a: [3.0, 3.0, 1.0, 0.0, 3.0, 0.0]
     * Highest: 3.0 → Max score = 3.0 ✅
     * 
     * Results for Q3: [0.0, 0.0, 0.0, 0.0, 0.0, 0.0]
     * Highest: 0.0 → Parse Q3Tester.java → Count tests → Max score = 4.0 ✅
     * 
     * @param results List of all grading results
     * @return Map of question ID → max score
     */
    public static Map<String, Double> inferMaxScores(List<GradingResult> results) {
        
        Map<String, Double> maxScores = new HashMap<>();
        
        // Group results by question ID
        Map<String, List<GradingResult>> byQuestion = new HashMap<>();
        
        for (GradingResult result : results) {
            String questionId = result.getQuestionId();
            byQuestion.computeIfAbsent(questionId, k -> new ArrayList<>()).add(result);
        }
        
        // Infer max score for each question
        for (Map.Entry<String, List<GradingResult>> entry : byQuestion.entrySet()) {
            
            String questionId = entry.getKey();
            List<GradingResult> questionResults = entry.getValue();
            
            // Find highest score achieved
            double highestScore = questionResults.stream()
                .mapToDouble(GradingResult::getScore)
                .max()
                .orElse(0.0);
            
            if (highestScore > 0) {
                // At least one student got points → use highest score as max
                maxScores.put(questionId, highestScore);
                
            } else {
                // All students scored 0 → parse tester to count tests (FIX 3)
                double inferredMax = getMaxScoreFromTester(questionId);
                maxScores.put(questionId, inferredMax);
            }
        }
        
        return maxScores;
    }
    
    /**
     * Gets max score by parsing tester file (FIX 3)
     * 
     * WHEN USED:
     * - All students scored 0 on a question
     * - We need to know max possible score
     * - Parse tester file to count tests
     * 
     * PARSING STRATEGY:
     * 1. Build tester filename: Q1a → Q1aTester.java
     * 2. Read tester file from resources/input/testers/
     * 3. Count occurrences of "score +="
     * 4. Each "score +=" represents 1 test (usually 1 point)
     * 5. Return count as max score
     * 
     * EXAMPLE TESTER:
     * ```
     * public class Q3Tester {
     *   public static void main(String[] args) {
     *     double score = 0;
     *     if (test1()) score += 1;  // Found! Count = 1
     *     if (test2()) score += 1;  // Found! Count = 2
     *     if (test3()) score += 1;  // Found! Count = 3
     *     if (test4()) score += 1;  // Found! Count = 4
     *     System.out.println(score);
     *   }
     * }
     * ```
     * Result: 4 occurrences → Max score = 4.0
     * 
     * ASSUMPTIONS:
     * - Each test awards 1 point
     * - Testers use "score +=" pattern
     * - This works for most standard testers
     * 
     * LIMITATIONS:
     * - Doesn't handle weighted tests (score += 2)
     * - Doesn't handle different patterns
     * - Best effort approach
     * 
     * @param questionId Question ID (e.g., "Q1a")
     * @return Inferred max score (0.0 if parsing fails)
     */
    private static double getMaxScoreFromTester(String questionId) {
        
        try {
            // Build tester filename
            String testerFilename = questionId + "Tester.java";
            
            // Build path to tester
            Path testerPath = PathConfig.INPUT_TESTERS.resolve(testerFilename);
            
            // Check if tester exists
            if (!Files.exists(testerPath)) {
                // Tester not found - return 0.0
                return 0.0;
            }
            
            // Read tester file
            String testerContent = Files.readString(testerPath);
            
            // Count occurrences of "score +="
            Pattern pattern = Pattern.compile("score\\s*\\+=");
            Matcher matcher = pattern.matcher(testerContent);
            
            int count = 0;
            while (matcher.find()) {
                count++;
            }
            
            // Return count as max score
            return (double) count;
            
        } catch (IOException e) {
            // Parsing failed - return 0.0
            return 0.0;
        }
    }
    
    /**
     * Updates all results with inferred max scores
     * 
     * WORKFLOW:
     * 1. Infer max scores for all questions
     * 2. For each result:
     *    a. Get max score for this question
     *    b. Create new result with updated max score
     * 3. Return list of updated results
     * 
     * IMMUTABILITY:
     * - Original results are unchanged
     * - Returns new list of new GradingResult objects
     * - Uses GradingResult.withMaxScore() method
     * 
     * @param results Original grading results
     * @return Updated results with max scores
     */
    public static List<GradingResult> updateWithMaxScores(List<GradingResult> results) {
        
        // Infer max scores
        Map<String, Double> maxScores = inferMaxScores(results);
        
        // Update each result
        List<GradingResult> updatedResults = new ArrayList<>();
        
        for (GradingResult result : results) {
            String questionId = result.getQuestionId();
            double maxScore = maxScores.getOrDefault(questionId, 0.0);
            
            // Create new result with updated max score
            GradingResult updated = result.withMaxScore(maxScore);
            updatedResults.add(updated);
        }
        
        return updatedResults;
    }
    
    /**
     * Calculates total score for a student
     * 
     * SUMS:
     * - All scores across all questions for one student
     * 
     * EXAMPLE:
     * Student results: Q1a:3.0, Q1b:3.0, Q2a:5.0, Q2b:1.0, Q3:0.0
     * Total: 3.0 + 3.0 + 5.0 + 1.0 + 0.0 = 12.0
     * 
     * @param studentResults All results for one student
     * @return Total score
     */
    public static double calculateTotalScore(List<GradingResult> studentResults) {
        return studentResults.stream()
            .mapToDouble(GradingResult::getScore)
            .sum();
    }
    
    /**
     * Calculates total max score for a student
     * 
     * SUMS:
     * - All max scores across all questions
     * 
     * EXAMPLE:
     * Max scores: Q1a:3.0, Q1b:3.0, Q2a:5.0, Q2b:5.0, Q3:4.0
     * Total max: 3.0 + 3.0 + 5.0 + 5.0 + 4.0 = 20.0
     * 
     * @param studentResults All results for one student
     * @return Total max score
     */
    public static double calculateTotalMaxScore(List<GradingResult> studentResults) {
        return studentResults.stream()
            .mapToDouble(GradingResult::getMaxScore)
            .sum();
    }
    
    /**
     * Groups results by student username
     * 
     * GROUPS:
     * - All results for each student together
     * 
     * RETURNS:
     * Map of username → list of results
     * 
     * EXAMPLE:
     * {
     *   "ping.lee.2023": [Q1a result, Q1b result, Q2a result, Q2b result, Q3 result],
     *   "chee.teo.2022": [Q1a result, Q1b result, Q2a result, Q2b result, Q3 result],
     *   ...
     * }
     * 
     * USEFUL FOR:
     * - Displaying results per student
     * - Calculating per-student totals
     * - Generating student-specific reports
     * 
     * @param results All grading results
     * @return Map of username → student's results
     */
    public static Map<String, List<GradingResult>> groupByStudent(List<GradingResult> results) {
        
        Map<String, List<GradingResult>> byStudent = new LinkedHashMap<>();
        
        for (GradingResult result : results) {
            String username = result.getStudent().getId();
            byStudent.computeIfAbsent(username, k -> new ArrayList<>()).add(result);
        }
        
        return byStudent;
    }
    
    /**
     * Groups results by question ID
     * 
     * GROUPS:
     * - All results for each question together
     * 
     * RETURNS:
     * Map of question ID → list of results
     * 
     * EXAMPLE:
     * {
     *   "Q1a": [ping's result, chee's result, david's result, ...],
     *   "Q1b": [ping's result, chee's result, david's result, ...],
     *   ...
     * }
     * 
     * USEFUL FOR:
     * - Analyzing question difficulty
     * - Finding which questions students struggled with
     * - Calculating per-question statistics
     * 
     * @param results All grading results
     * @return Map of question ID → question's results
     */
    public static Map<String, List<GradingResult>> groupByQuestion(List<GradingResult> results) {
        
        Map<String, List<GradingResult>> byQuestion = new LinkedHashMap<>();
        
        for (GradingResult result : results) {
            String questionId = result.getQuestionId();
            byQuestion.computeIfAbsent(questionId, k -> new ArrayList<>()).add(result);
        }
        
        return byQuestion;
    }
    
    /**
     * Calculates average score for a question
     * 
     * CALCULATES:
     * - Mean score across all students for one question
     * 
     * EXAMPLE:
     * Q1a scores: [3.0, 3.0, 1.0, 0.0, 3.0, 0.0]
     * Average: (3.0 + 3.0 + 1.0 + 0.0 + 3.0 + 0.0) / 6 = 1.67
     * 
     * @param questionResults All results for one question
     * @return Average score
     */
    public static double calculateAverageScore(List<GradingResult> questionResults) {
        
        if (questionResults.isEmpty()) {
            return 0.0;
        }
        
        double total = questionResults.stream()
            .mapToDouble(GradingResult::getScore)
            .sum();
        
        return total / questionResults.size();
    }
    
    /**
     * Counts how many students passed a question (score > 0)
     * 
     * @param questionResults All results for one question
     * @return Number of students who got at least some points
     */
    public static long countPassed(List<GradingResult> questionResults) {
        return questionResults.stream()
            .filter(r -> r.getScore() > 0)
            .count();
    }
    
    /**
     * Counts how many students got perfect score on a question
     * 
     * @param questionResults All results for one question
     * @return Number of students with perfect score
     */
    public static long countPerfect(List<GradingResult> questionResults) {
        return questionResults.stream()
            .filter(GradingResult::isPerfect)
            .count();
    }
    
    /**
     * Counts how many students failed a question (score = 0)
     * 
     * @param questionResults All results for one question
     * @return Number of students with zero score
     */
    public static long countFailed(List<GradingResult> questionResults) {
        return questionResults.stream()
            .filter(r -> r.getScore() == 0)
            .count();
    }
}