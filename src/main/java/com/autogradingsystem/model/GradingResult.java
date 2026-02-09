package com.autogradingsystem.model;

import java.util.Objects;

/**
 * GradingResult - Result of Grading One Task for One Student
 * 
 * PURPOSE:
 * - Stores the outcome of grading a single question for a single student
 * - Tracks raw points (not percentages) - e.g., 3.0/3.0, 4.0/4.0
 * - Used for Phase 4 reporting
 * 
 * RAW POINTS SYSTEM:
 * - Different questions have different max scores
 * - Q1a: 3 tests = 3.0 max
 * - Q1b: 3 tests = 3.0 max
 * - Q3: 4 tests = 4.0 max
 * - Scores are NOT normalized to 100
 * 
 * @author IS442 Team
 * @version 2.0 (Updated for raw points)
 */
public class GradingResult {
    
    private final Student student;
    private final GradingTask task;
    private final double score;        // Points earned (e.g., 3.0)
    private final double maxScore;     // Max possible (e.g., 3.0) - 0.0 if unknown
    private final String output;
    private final String status;
    
    /**
     * Constructor with max score (preferred)
     * 
     * @param student Student who was graded
     * @param task Task that was graded
     * @param score Score achieved (raw points)
     * @param maxScore Maximum possible score for this question
     * @param output Full console output
     * @param status Grading status
     */
    public GradingResult(Student student, GradingTask task, double score, double maxScore, 
                        String output, String status) {
        this.student = student;
        this.task = task;
        this.score = score;
        this.maxScore = maxScore;
        this.output = output;
        this.status = status;
    }
    
    /**
     * Constructor with max score, auto-determine status
     * 
     * @param student Student who was graded
     * @param task Task that was graded
     * @param score Score achieved
     * @param maxScore Maximum possible score
     * @param output Console output
     */
    public GradingResult(Student student, GradingTask task, double score, double maxScore, String output) {
        this(student, task, score, maxScore, output, determineStatus(score, maxScore, output));
    }
    
    /**
     * Constructor without max score (legacy - max defaults to 0.0)
     * Max score will be inferred later from all results
     * 
     * @param student Student who was graded
     * @param task Task that was graded
     * @param score Score achieved
     * @param output Console output
     * @param status Status
     */
    public GradingResult(Student student, GradingTask task, double score, String output, String status) {
        this(student, task, score, 0.0, output, status);
    }
    
    /**
     * Constructor without max score, auto-determine status
     * 
     * @param student Student
     * @param task Task
     * @param score Score achieved
     * @param output Console output
     */
    public GradingResult(Student student, GradingTask task, double score, String output) {
        this(student, task, score, 0.0, output, determineStatus(score, 0.0, output));
    }
    
    /**
     * Determines status from score, max score, and output
     * 
     * @param score Score achieved
     * @param maxScore Maximum possible (0.0 if unknown)
     * @param output Console output
     * @return Status string
     */
    private static String determineStatus(double score, double maxScore, String output) {
        if (output == null || output.isEmpty()) {
            return "NO_OUTPUT";
        }
        
        if (output.contains("Compilation Failed") || output.contains("[javac ERROR]")) {
            return "COMPILATION_FAILED";
        }
        
        if (output.contains("File not found") || output.contains("FileNotFoundException")) {
            return "FILE_NOT_FOUND";
        }
        
        if (output.contains("Timed Out") || output.contains("Timeout")) {
            return "TIMEOUT";
        }
        
        if (output.contains("Error:")) {
            return "RUNTIME_ERROR";
        }
        
        if (score == 0.0) {
            return "FAILED";
        }
        
        // Check if perfect (only if we know max score)
        if (maxScore > 0.0 && score >= maxScore) {
            return "PERFECT";
        }
        
        return "PARTIAL";
    }
    
    // =========================================================================
    // GETTERS
    // =========================================================================
    
    public Student getStudent() { return student; }
    public GradingTask getTask() { return task; }
    public double getScore() { return score; }
    public double getMaxScore() { return maxScore; }
    public String getOutput() { return output; }
    public String getStatus() { return status; }
    public String getStudentUsername() { return student.getUsername(); }
    public String getQuestionId() { return task.getQuestionId(); }
    
    /**
     * Checks if max score is known
     * 
     * @return true if maxScore > 0, false otherwise
     */
    public boolean hasMaxScore() {
        return maxScore > 0.0;
    }
    
    /**
     * Calculates percentage (only if max score known)
     * 
     * @return Percentage (0-100) or -1 if max unknown
     */
    public double getPercentage() {
        if (maxScore <= 0.0) {
            return -1.0;  // Unknown
        }
        return (score / maxScore) * 100.0;
    }
    
    /**
     * Checks if grading was successful (score > 0 and no errors)
     * 
     * @return true if successful, false otherwise
     */
    public boolean isSuccessful() {
        return score > 0.0 && 
               !status.equals("COMPILATION_FAILED") && 
               !status.equals("FILE_NOT_FOUND") &&
               !status.equals("TIMEOUT") &&
               !status.equals("RUNTIME_ERROR");
    }
    
    /**
     * Checks if student got perfect score
     * 
     * @return true if score == maxScore (or maxScore unknown and score > 0), false otherwise
     */
    public boolean isPerfect() {
        if (maxScore > 0.0) {
            return score >= maxScore;
        }
        return false;  // Can't determine without max score
    }
    
    /**
     * Checks if grading failed completely (score == 0)
     * 
     * @return true if score == 0.0, false otherwise
     */
    public boolean isFailed() {
        return score == 0.0;
    }
    
    /**
     * Updates max score (for when inferring from results)
     * Returns new GradingResult with updated max score
     * 
     * @param newMaxScore Max score to set
     * @return New GradingResult with updated max score
     */
    public GradingResult withMaxScore(double newMaxScore) {
        return new GradingResult(student, task, score, newMaxScore, output, status);
    }
    
    // =========================================================================
    // UTILITY METHODS
    // =========================================================================
    
    /**
     * Returns summary string for logging
     * 
     * EXAMPLE:
     * "ping.lee.2023 | Q1a | 3.0/3.0 | PERFECT"
     * "ping.lee.2023 | Q1b | 2.0/3.0 | PARTIAL"
     * 
     * @return Summary string
     */
    public String getSummary() {
        if (hasMaxScore()) {
            return String.format("%s | %s | %.1f/%.1f | %s", 
                student.getUsername(), 
                task.getQuestionId(), 
                score,
                maxScore,
                status
            );
        } else {
            return String.format("%s | %s | %.1f | %s", 
                student.getUsername(), 
                task.getQuestionId(), 
                score, 
                status
            );
        }
    }
    
    /**
     * Returns score display string
     * 
     * EXAMPLE:
     * "3.0" (if max unknown)
     * "3.0/3.0" (if max known)
     * 
     * @return Score display string
     */
    public String getScoreDisplay() {
        if (hasMaxScore()) {
            return String.format("%.1f/%.1f", score, maxScore);
        } else {
            return String.format("%.1f", score);
        }
    }
    
    /**
     * Returns detailed string representation for debugging
     * 
     * @return String representation
     */
    @Override
    public String toString() {
        if (hasMaxScore()) {
            return "GradingResult{student=" + student.getUsername() +
                   ", question=" + task.getQuestionId() +
                   ", score=" + score + "/" + maxScore +
                   ", status=" + status + '}';
        } else {
            return "GradingResult{student=" + student.getUsername() +
                   ", question=" + task.getQuestionId() +
                   ", score=" + score +
                   ", status=" + status + '}';
        }
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        GradingResult other = (GradingResult) obj;
        return student.equals(other.student) && task.equals(other.task);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(student, task);
    }
}