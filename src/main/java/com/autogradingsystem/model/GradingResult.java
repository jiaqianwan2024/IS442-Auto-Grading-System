package com.autogradingsystem.model;

/**
 * GradingResult - Represents the Result of Grading One Task
 * 
 * PURPOSE:
 * - Encapsulates the complete result of grading one question for one student
 * - Stores raw points (not percentages) - percentages calculated later
 * - Shared across execution and analysis services
 * - Supports updating with max scores after initial grading
 * 
 * CREATED BY:
 * - ExecutionController during Phase 3 (Grading)
 * 
 * USED BY:
 * - ScoreAnalyzer for calculating totals and percentages
 * - AnalysisController for displaying results
 * 
 * DESIGN:
 * - Immutable except for maxScore (updated after inference)
 * - Multiple constructors for different use cases
 * - Status auto-determined from score/output
 * 
 * NO CHANGES FROM v3.0:
 * - Already well-designed
 * - Package stays in global model/ (shared across services)
 * - Added comprehensive JavaDoc
 * 
 * @author IS442 Team
 * @version 4.0 (Spring Boot Microservices Structure)
 */
public class GradingResult {
    
    // ================================================================
    // FIELDS
    // ================================================================
    
    /**
     * Student who was graded
     */
    private final Student student;
    
    /**
     * Task that was graded
     */
    private final GradingTask task;
    
    /**
     * Raw points earned
     * 
     * EXAMPLES:
     * - 3.0 (full marks on 3-point question)
     * - 1.5 (partial credit)
     * - 0.0 (no credit)
     * 
     * NOT A PERCENTAGE:
     * - This is absolute points, not percentage
     * - 3.0 points out of 3.0 max = 100%
     * - Percentage calculated using: (score / maxScore) * 100
     */
    private final double score;
    
    /**
     * Maximum possible points for this question
     * 
     * INITIALLY:
     * - May be 0.0 if not known yet
     * - Updated by ScoreAnalyzer after inference
     * 
     * INFERENCE SOURCES:
     * - Highest score achieved by any student
     * - OR parsed from tester file if all students scored 0
     * 
     * EXAMPLES:
     * - 3.0 (for a 3-point question)
     * - 5.0 (for a 5-point question)
     * - 4.0 (for a 4-point question)
     */
    private final double maxScore;
    
    /**
     * Full console output from tester execution
     * 
     * CONTAINS:
     * - All test results
     * - Print statements from tester
     * - Final score on last line
     * - Any error messages
     * 
     * USEFUL FOR:
     * - Debugging failures
     * - Understanding partial credit
     * - Manual review
     */
    private final String output;
    
    /**
     * Grading status
     * 
     * POSSIBLE VALUES:
     * - "PERFECT" - Full marks achieved
     * - "PARTIAL" - Some credit earned
     * - "FAILED" - No credit (but code ran)
     * - "COMPILATION_FAILED" - Code didn't compile
     * - "FILE_NOT_FOUND" - Student didn't submit file
     * - "TIMEOUT" - Execution exceeded time limit
     * - "ERROR" - Other execution error
     */
    private final String status;
    
    // ================================================================
    // CONSTRUCTORS
    // ================================================================
    
    /**
     * Full constructor - All fields specified
     * 
     * USE CASE:
     * - When creating result with known max score
     * - After max score inference
     * 
     * @param student Student who was graded
     * @param task Task that was graded
     * @param score Raw points earned
     * @param maxScore Maximum possible points
     * @param output Full console output
     * @param status Grading status
     */
    public GradingResult(Student student, GradingTask task, double score,
                        double maxScore, String output, String status) {
        this.student = student;
        this.task = task;
        this.score = score;
        this.maxScore = maxScore;
        this.output = output;
        this.status = status;
    }
    
    /**
     * Constructor without max score
     * 
     * USE CASE:
     * - Initial grading (max score not yet known)
     * - Max score will be inferred later by ScoreAnalyzer
     * 
     * DEFAULT:
     * - Sets maxScore = 0.0
     * 
     * @param student Student who was graded
     * @param task Task that was graded
     * @param score Raw points earned
     * @param output Full console output
     * @param status Grading status
     */
    public GradingResult(Student student, GradingTask task, double score,
                        String output, String status) {
        this(student, task, score, 0.0, output, status);
    }
    
    /**
     * Constructor with auto-determined status
     * 
     * USE CASE:
     * - Successful grading (code ran, got score)
     * - Status determined from score and output
     * 
     * STATUS LOGIC:
     * - If score > 0 and maxScore > 0 and score == maxScore → "PERFECT"
     * - If score > 0 → "PARTIAL"
     * - If score == 0 → "FAILED"
     * 
     * @param student Student who was graded
     * @param task Task that was graded
     * @param score Raw points earned
     * @param output Full console output
     */
    public GradingResult(Student student, GradingTask task, double score, String output) {
        this(student, task, score, 0.0, output, determineStatus(score, 0.0, output));
    }
    
    // ================================================================
    // GETTERS
    // ================================================================
    
    /**
     * Gets the student who was graded
     * 
     * @return Student object
     */
    public Student getStudent() {
        return student;
    }
    
    /**
     * Gets the task that was graded
     * 
     * @return GradingTask object
     */
    public GradingTask getTask() {
        return task;
    }
    
    /**
     * Gets the question ID (convenience method)
     * 
     * EQUIVALENT TO:
     * result.getTask().getQuestionId()
     * 
     * @return Question ID (e.g., "Q1a")
     */
    public String getQuestionId() {
        return task.getQuestionId();
    }
    
    /**
     * Gets the raw points earned
     * 
     * @return Score in points (not percentage)
     */
    public double getScore() {
        return score;
    }
    
    /**
     * Gets the maximum possible points
     * 
     * @return Max score (0.0 if not yet inferred)
     */
    public double getMaxScore() {
        return maxScore;
    }
    
    /**
     * Gets the full console output
     * 
     * @return Output from tester execution
     */
    public String getOutput() {
        return output;
    }
    
    /**
     * Gets the grading status
     * 
     * @return Status string
     */
    public String getStatus() {
        return status;
    }
    
    // ================================================================
    // CALCULATED PROPERTIES
    // ================================================================
    
    /**
     * Calculates percentage score
     * 
     * FORMULA:
     * percentage = (score / maxScore) * 100
     * 
     * EXAMPLES:
     * - 3.0 / 3.0 * 100 = 100.0%
     * - 1.5 / 3.0 * 100 = 50.0%
     * - 0.0 / 3.0 * 100 = 0.0%
     * 
     * EDGE CASES:
     * - If maxScore is 0 → returns 0.0 (avoid division by zero)
     * 
     * @return Percentage score (0-100)
     */
    public double getPercentage() {
        if (maxScore <= 0) {
            return 0.0;
        }
        return (score / maxScore) * 100.0;
    }
    
    /**
     * Checks if student achieved perfect score
     * 
     * PERFECT CRITERIA:
     * - maxScore > 0 (known)
     * - score == maxScore (full marks)
     * 
     * @return true if perfect score, false otherwise
     */
    public boolean isPerfect() {
        return maxScore > 0 && Math.abs(score - maxScore) < 0.001;  // Use epsilon for float comparison
    }
    
    /**
     * Checks if student received partial credit
     * 
     * PARTIAL CRITERIA:
     * - score > 0 (got some points)
     * - score < maxScore (but not full marks)
     * 
     * @return true if partial credit, false otherwise
     */
    public boolean isPartial() {
        return score > 0 && !isPerfect();
    }
    
    /**
     * Checks if student failed (no credit)
     * 
     * FAILED CRITERIA:
     * - score == 0 (no points earned)
     * - Status is not compilation/file error
     * 
     * @return true if failed, false otherwise
     */
    public boolean isFailed() {
        return score == 0 && 
               !status.equals("COMPILATION_FAILED") && 
               !status.equals("FILE_NOT_FOUND");
    }
    
    // ================================================================
    // TRANSFORMATION METHODS
    // ================================================================
    
    /**
     * Creates a new GradingResult with updated max score
     * 
     * USE CASE:
     * - After max score inference by ScoreAnalyzer
     * - Updating results with inferred max scores
     * 
     * IMMUTABILITY:
     * - Original result is unchanged
     * - Returns new GradingResult with updated maxScore
     * - Status is recalculated based on new max score
     * 
     * @param newMax New maximum score
     * @return New GradingResult with updated max score
     */
    public GradingResult withMaxScore(double newMax) {
        String newStatus = determineStatus(this.score, newMax, this.output);
        return new GradingResult(
            this.student,
            this.task,
            this.score,
            newMax,
            this.output,
            newStatus
        );
    }
    
    // ================================================================
    // HELPER METHODS
    // ================================================================
    
    /**
     * Determines grading status from score and output
     * 
     * LOGIC:
     * 1. If output contains "FILE_NOT_FOUND" → "FILE_NOT_FOUND"
     * 2. If output contains "COMPILATION_FAILED" → "COMPILATION_FAILED"
     * 3. If score > 0 and maxScore > 0 and score == maxScore → "PERFECT"
     * 4. If score > 0 → "PARTIAL"
     * 5. Otherwise → "FAILED"
     * 
     * @param score Points earned
     * @param maxScore Maximum possible points
     * @param output Console output
     * @return Status string
     */
    private static String determineStatus(double score, double maxScore, String output) {
        
        // Check for error conditions in output
        if (output != null) {
            if (output.contains("FILE_NOT_FOUND") || output.contains("File not found")) {
                return "FILE_NOT_FOUND";
            }
            if (output.contains("COMPILATION_FAILED") || output.contains("Compilation failed")) {
                return "COMPILATION_FAILED";
            }
            if (output.contains("TIMEOUT") || output.contains("Timed out")) {
                return "TIMEOUT";
            }
        }
        
        // Determine status from score
        if (maxScore > 0 && Math.abs(score - maxScore) < 0.001) {
            return "PERFECT";
        } else if (score > 0) {
            return "PARTIAL";
        } else {
            return "FAILED";
        }
    }
    
    // ================================================================
    // OBJECT METHODS
    // ================================================================
    
    /**
     * String representation for debugging
     * 
     * FORMAT:
     * GradingResult{student='ping.lee.2023', question='Q1a', score=3.0/3.0, status=PERFECT}
     * 
     * @return Human-readable representation
     */
    @Override
    public String toString() {
        return String.format(
            "GradingResult{student='%s', question='%s', score=%.1f/%.1f, status=%s}",
            student.getId(),
            task.getQuestionId(),
            score,
            maxScore,
            status
        );
    }
}