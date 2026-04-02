package com.autogradingsystem.analysis.service;

import com.autogradingsystem.model.GradingResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * ScoreAnalyzer - Analyzes Grading Results and Infers Max Scores
 * * PURPOSE:
 * - Infers maximum possible scores for each question
 * - Updates results with inferred max scores
 * - Groups results by student for reporting
 * - Calculates totals and statistics
 * * MAX SCORE INFERENCE (FIX 3):
 * Strategy 1: Use highest score achieved by any student
 * Strategy 2: If all students scored 0, parse tester file to count tests
 * * WHY NEEDED?
 * - We don't hardcode max scores anywhere
 * - System must figure out max scores automatically
 * - Enables flexible exam structures (any number of tests per question)
 * 
 */
public class ScoreAnalyzer {
    
    /**
     * Infers maximum scores for each question by ALWAYS parsing the Master Tester file.
     * This avoids trusting untrusted student output (prevents Infinite Loop inflation)
     * and accurately handles fractional scores like (3.0/12).
     * * @param results List of all grading results
     * @return Map of question ID → max score
     */
    /** Path-aware overload for multi-assessment. */
    public static Map<String, Double> inferMaxScores(List<GradingResult> results, Path testersDir) {
        Map<String, Double> maxScores = new HashMap<>();
        for (GradingResult result : results) {
            String questionId = result.getQuestionId();
            if (!maxScores.containsKey(questionId)) {
                maxScores.put(questionId, getMaxScoreFromTester(questionId, testersDir));
            }
        }
        return maxScores;
    }

    public static Map<String, Double> inferMaxScores(List<GradingResult> results) {
        throw new UnsupportedOperationException("Use inferMaxScores(results, testersDir) in assessment-scoped flow.");
    }

    // original kept for backward compatibility — delegates above
    static Map<String, Double> inferMaxScoresLegacy(List<GradingResult> results) {
        
        Map<String, Double> maxScores = new HashMap<>();
        
        // Find all unique question IDs in this assessment
        for (GradingResult result : results) {
            String questionId = result.getQuestionId();
            if (!maxScores.containsKey(questionId)) {
                // ALWAYS parse the tester file to find the true mathematically perfect max score
                maxScores.put(questionId, getMaxScoreFromTester(questionId));
            }
        }
        
        return maxScores;
    }
    
    /**
     * Gets max score by parsing tester file (FIX 3)
     * * WHEN USED:
     * - All students scored 0 on a question
     * - We need to know max possible score
     * - Parse tester file to count tests
     * * PARSING STRATEGY:
     * 1. Build tester filename: Q1a → Q1aTester.java
     * 2. Read tester file from resources/input/testers/
     * 3. Count occurrences of "score +="
     * 4. Each "score +=" represents 1 test (usually 1 point)
     * 5. Return count as max score
     * * EXAMPLE TESTER:
     * ```
     * public class Q3Tester {
     * public static void main(String[] args) {
     * double score = 0;
     * if (test1()) score += 1;  // Found! Count = 1
     * if (test2()) score += 1;  // Found! Count = 2
     * if (test3()) score += 1;  // Found! Count = 3
     * if (test4()) score += 1;  // Found! Count = 4
     * System.out.println(score);
     * }
     * }
     * ```
     * Result: 4 occurrences → Max score = 4.0
     * * ASSUMPTIONS:
     * - Each test awards 1 point
     * - Testers use "score +=" pattern
     * - This works for most standard testers
     * * LIMITATIONS:
     * - Doesn't handle weighted tests (score += 2)
     * - Doesn't handle different patterns
     * - Best effort approach
     * * @param questionId Question ID (e.g., "Q1a")
     * @return Inferred max score (0.0 if parsing fails)
     */
    /**
     * Overload that accepts a per-assessment testers path.
     * Used by multi-assessment grading so each assessment reads its own testers.
     */
    public static double getMaxScoreFromTester(String questionId, Path testersDir) {
        try {
            String formattedId = "Q" + questionId.substring(1).toLowerCase();
            String testerFilename = formattedId + "Tester.java";
            Path testerPath = testersDir.resolve(testerFilename);
            if (!Files.exists(testerPath)) {
                testerPath = testersDir.resolve(questionId + "Tester.java");
            }
            if (!Files.exists(testerPath)) return 0.0;
            String testerContent = Files.readString(testerPath);
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("score\\s*\\+=\\s*\\(?\\s*([0-9.]+)\\s*(?:/\\s*([0-9.]+))?\\s*\\)?");
            java.util.regex.Matcher matcher = pattern.matcher(testerContent);
            double totalMax = 0.0;
            while (matcher.find()) {
                double numerator = Double.parseDouble(matcher.group(1));
                if (matcher.group(2) != null) {
                    totalMax += numerator / Double.parseDouble(matcher.group(2));
                } else {
                    totalMax += numerator;
                }
            }
            return Math.round(totalMax * 100.0) / 100.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    public static double getMaxScoreFromTester(String questionId) {
        throw new UnsupportedOperationException("Use getMaxScoreFromTester(questionId, testersDir) in assessment-scoped flow.");
    }
    
    /**
     * Updates all results with inferred max scores
     * * WORKFLOW:
     * 1. Infer max scores for all questions
     * 2. For each result:
     * a. Get max score for this question
     * b. Create new result with updated max score
     * 3. Return list of updated results
     * * IMMUTABILITY:
     * - Original results are unchanged
     * - Returns new list of new GradingResult objects
     * - Uses GradingResult.withMaxScore() method
     * * @param results Original grading results
     * @return Updated results with max scores
     */
    /** Path-aware overload for multi-assessment. */
    public static List<GradingResult> updateWithMaxScores(List<GradingResult> results, Path testersDir) {
        Map<String, Double> maxScores = inferMaxScores(results, testersDir);
        List<GradingResult> updatedResults = new ArrayList<>();
        for (GradingResult result : results) {
            updatedResults.add(result.withMaxScore(maxScores.getOrDefault(result.getQuestionId(), 0.0)));
        }
        return updatedResults;
    }

    public static List<GradingResult> updateWithMaxScores(List<GradingResult> results) {
        throw new UnsupportedOperationException("Use updateWithMaxScores(results, testersDir) in assessment-scoped flow.");
    }

    // original kept for backward compat
    static List<GradingResult> updateWithMaxScoresLegacy(List<GradingResult> results) {
        
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
     * * SUMS:
     * - All scores across all questions for one student
     * * EXAMPLE:
     * Student results: Q1a:3.0, Q1b:3.0, Q2a:5.0, Q2b:1.0, Q3:0.0
     * Total: 3.0 + 3.0 + 5.0 + 1.0 + 0.0 = 12.0
     * * @param studentResults All results for one student
     * @return Total score
     */
    public static double calculateTotalScore(List<GradingResult> studentResults) {
        return studentResults.stream()
            .mapToDouble(GradingResult::getScore)
            .sum();
    }
    
    /**
     * Calculates total max score for a student
     * * SUMS:
     * - All max scores across all questions
     * * EXAMPLE:
     * Max scores: Q1a:3.0, Q1b:3.0, Q2a:5.0, Q2b:5.0, Q3:4.0
     * Total max: 3.0 + 3.0 + 5.0 + 5.0 + 4.0 = 20.0
     * * @param studentResults All results for one student
     * @return Total max score
     */
    public static double calculateTotalMaxScore(List<GradingResult> studentResults) {
        return studentResults.stream()
            .mapToDouble(GradingResult::getMaxScore)
            .sum();
    }
    
    /**
     * Groups results by student username
     * * GROUPS:
     * - All results for each student together
     * * RETURNS:
     * Map of username → list of results
     * * EXAMPLE:
     * {
     * "ping.lee.2023": [Q1a result, Q1b result, Q2a result, Q2b result, Q3 result],
     * "chee.teo.2022": [Q1a result, Q1b result, Q2a result, Q2b result, Q3 result],
     * ...
     * }
     * * USEFUL FOR:
     * - Displaying results per student
     * - Calculating per-student totals
     * - Generating student-specific reports
     * * @param results All grading results
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
     * * GROUPS:
     * - All results for each question together
     * * RETURNS:
     * Map of question ID → list of results
     * * EXAMPLE:
     * {
     * "Q1a": [ping's result, chee's result, david's result, ...],
     * "Q1b": [ping's result, chee's result, david's result, ...],
     * ...
     * }
     * * USEFUL FOR:
     * - Analyzing question difficulty
     * - Finding which questions students struggled with
     * - Calculating per-question statistics
     * * @param results All grading results
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
     * * CALCULATES:
     * - Mean score across all students for one question
     * * EXAMPLE:
     * Q1a scores: [3.0, 3.0, 1.0, 0.0, 3.0, 0.0]
     * Average: (3.0 + 3.0 + 1.0 + 0.0 + 3.0 + 0.0) / 6 = 1.67
     * * @param questionResults All results for one question
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
     * * @param questionResults All results for one question
     * @return Number of students who got at least some points
     */
    public static long countPassed(List<GradingResult> questionResults) {
        return questionResults.stream()
            .filter(r -> r.getScore() > 0)
            .count();
    }
    
    /**
     * Counts how many students got perfect score on a question
     * * @param questionResults All results for one question
     * @return Number of students with perfect score
     */
    public static long countPerfect(List<GradingResult> questionResults) {
        return questionResults.stream()
            .filter(GradingResult::isPerfect)
            .count();
    }
    
    /**
     * Counts how many students failed a question (score = 0)
     * * @param questionResults All results for one question
     * @return Number of students with zero score
     */
    public static long countFailed(List<GradingResult> questionResults) {
        return questionResults.stream()
            .filter(r -> r.getScore() == 0)
            .count();
    }
}
