package com.autogradingsystem.model;

import java.util.Objects;

/**
 * GradingTask - Data Model for a Single Grading Task
 * 
 * PURPOSE:
 * - Represents one question to be graded
 * - Contains all information needed to grade that question
 * - Links question, tester, folder, and file together
 * 
 * DESIGN PATTERN: Value Object / Data Transfer Object (DTO)
 * - Immutable after construction (all fields final)
 * - No business logic - just data storage
 * - Easy to pass between components
 * - Thread-safe (immutable)
 * 
 * EXAMPLE DATA:
 * GradingTask {
 *   questionId: "Q1a",
 *   testerFile: "Q1aTester.java",
 *   studentFolder: "Q1",
 *   studentFile: "Q1a.java"
 * }
 * 
 * USAGE IN GRADING LOOP:
 * for (GradingTask task : plan.getTasks()) {
 *     String studentPath = studentBaseDir + "/" + task.getStudentFolder() + "/" + task.getStudentFile();
 *     String testerPath = testersDir + "/" + task.getTesterFile();
 *     
 *     // Inject tester, compile, run, parse score...
 * }
 * 
 * NULL TESTER HANDLING:
 * - testerFile CAN be null if no tester found for question
 * - Grading logic should check for null and score as 0
 * - This allows graceful degradation when testers are missing
 * 
 * @author IS442 Team
 * @version 1.0
 */
public class GradingTask {
    
    // Question identifier (e.g., "Q1a", "Q2b")
    private final String questionId;
    
    // Tester filename (e.g., "Q1aTester.java")
    // CAN BE NULL if no tester found for this question
    private final String testerFile;
    
    // Student's question folder (e.g., "Q1", "Q2")
    private final String studentFolder;
    
    // Student's Java file (e.g., "Q1a.java")
    private final String studentFile;
    
    /**
     * Constructor - Creates immutable GradingTask
     * 
     * VALIDATION:
     * - questionId, studentFolder, studentFile must not be null
     * - testerFile CAN be null (indicates missing tester)
     * 
     * NULL TESTER EXAMPLE:
     * GradingTask task = new GradingTask("Q1a", null, "Q1", "Q1a.java");
     * // Valid - represents question with no tester (will score 0)
     * 
     * @param questionId Question ID (e.g., "Q1a") - must not be null
     * @param testerFile Tester filename (e.g., "Q1aTester.java") - can be null
     * @param studentFolder Student folder name (e.g., "Q1") - must not be null
     * @param studentFile Student filename (e.g., "Q1a.java") - must not be null
     * @throws IllegalArgumentException if required fields are null
     */
    public GradingTask(String questionId, String testerFile, String studentFolder, String studentFile) {
        
        // Validate required fields
        if (questionId == null || questionId.trim().isEmpty()) {
            throw new IllegalArgumentException("Question ID cannot be null or empty");
        }
        if (studentFolder == null || studentFolder.trim().isEmpty()) {
            throw new IllegalArgumentException("Student folder cannot be null or empty");
        }
        if (studentFile == null || studentFile.trim().isEmpty()) {
            throw new IllegalArgumentException("Student file cannot be null or empty");
        }
        
        // Note: testerFile is allowed to be null
        
        this.questionId = questionId;
        this.testerFile = testerFile;
        this.studentFolder = studentFolder;
        this.studentFile = studentFile;
    }
    
    /**
     * Gets the question ID.
     * 
     * EXAMPLE:
     * String id = task.getQuestionId();  // "Q1a"
     * 
     * @return Question ID (never null)
     */
    public String getQuestionId() {
        return questionId;
    }
    
    /**
     * Gets the tester filename.
     * 
     * IMPORTANT: CAN RETURN NULL!
     * Always check for null before using:
     * 
     * EXAMPLE:
     * String tester = task.getTesterFile();
     * if (tester != null) {
     *     // Use tester
     * } else {
     *     // No tester - score as 0
     * }
     * 
     * @return Tester filename or null if no tester
     */
    public String getTesterFile() {
        return testerFile;
    }
    
    /**
     * Gets the student folder name.
     * 
     * EXAMPLE:
     * String folder = task.getStudentFolder();  // "Q1"
     * 
     * @return Student folder (never null)
     */
    public String getStudentFolder() {
        return studentFolder;
    }
    
    /**
     * Gets the student filename.
     * 
     * EXAMPLE:
     * String file = task.getStudentFile();  // "Q1a.java"
     * 
     * @return Student filename (never null)
     */
    public String getStudentFile() {
        return studentFile;
    }
    
    /**
     * Checks if this task has a tester.
     * 
     * RECOMMENDED: Use this before getTesterFile() to avoid null checks
     * 
     * EXAMPLE:
     * if (task.hasTester()) {
     *     String tester = task.getTesterFile();
     *     // Safe - tester is not null
     * } else {
     *     System.out.println("No tester for " + task.getQuestionId());
     * }
     * 
     * @return true if tester exists, false if null
     */
    public boolean hasTester() {
        return testerFile != null;
    }
    
    /**
     * Builds full path to student file.
     * Helper method for convenience.
     * 
     * EXAMPLE:
     * String basePath = "data/extracted/ping.lee.2023";
     * String fullPath = task.getStudentFilePath(basePath);
     * // Returns: "data/extracted/ping.lee.2023/Q1/Q1a.java"
     * 
     * CROSS-PLATFORM NOTE:
     * Uses "/" separator - Path.of() or Paths.get() will convert appropriately
     * 
     * @param studentBaseDirectory Base directory for student
     * @return Full path to student file
     */
    public String getStudentFilePath(String studentBaseDirectory) {
        return studentBaseDirectory + "/" + studentFolder + "/" + studentFile;
    }
    
    /**
     * Returns detailed string representation for debugging.
     * 
     * EXAMPLE OUTPUT:
     * GradingTask{Q1a, tester=Q1aTester.java, folder=Q1, file=Q1a.java}
     * 
     * OR if no tester:
     * GradingTask{Q1a, tester=NONE, folder=Q1, file=Q1a.java}
     * 
     * @return String representation
     */
    @Override
    public String toString() {
        return "GradingTask{" +
               "questionId='" + questionId + '\'' +
               ", tester=" + (testerFile != null ? testerFile : "NONE") +
               ", folder='" + studentFolder + '\'' +
               ", file='" + studentFile + '\'' +
               '}';
    }
    
    /**
     * Equality check based on content.
     * Two GradingTasks are equal if all fields match.
     * 
     * @param obj Object to compare
     * @return true if equal, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        GradingTask other = (GradingTask) obj;
        return questionId.equals(other.questionId) &&
               Objects.equals(testerFile, other.testerFile) &&
               studentFolder.equals(other.studentFolder) &&
               studentFile.equals(other.studentFile);
    }
    
    /**
     * Hash code based on content.
     * 
     * @return Hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(questionId, testerFile, studentFolder, studentFile);
    }
}