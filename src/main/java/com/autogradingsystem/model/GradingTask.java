package com.autogradingsystem.model;

/**
 * GradingTask - Represents One Question to be Graded
 * 
 * PURPOSE:
 * - Encapsulates all information needed to grade one question
 * - Shared across discovery, execution, and analysis services
 * - Immutable for thread safety
 * 
 * CONTAINS:
 * - Question ID (e.g., "Q1a")
 * - Tester filename (e.g., "Q1aTester.java")
 * - Student folder location (e.g., "Q1")
 * - Student filename (e.g., "Q1a.java")
 * 
 * CREATED BY:
 * - GradingPlanBuilder during Phase 2 (Discovery)
 * 
 * USED BY:
 * - ExecutionController for grading
 * - GradingResult for storing task information
 * 
 * NO CHANGES FROM v3.0:
 * - Already well-designed
 * - Package stays in global model/ (shared across services)
 * - Added comprehensive JavaDoc
 * 
 * @author IS442 Team
 * @version 4.0 (Spring Boot Microservices Structure)
 */
public class GradingTask {
    
    // ================================================================
    // FIELDS
    // ================================================================
    
    /**
     * Question identifier
     * 
     * EXAMPLES:
     * - "Q1a" (Question 1, part a)
     * - "Q1b" (Question 1, part b)
     * - "Q2a" (Question 2, part a)
     * - "Q3" (Question 3, single file)
     * 
     * USED FOR:
     * - Matching with testers
     * - Grouping results
     * - Display formatting
     */
    private final String questionId;
    
    /**
     * Tester filename
     * 
     * EXAMPLES:
     * - "Q1aTester.java"
     * - "Q1bTester.java"
     * - "Q3Tester.java"
     * 
     * USED FOR:
     * - Locating tester in resources/input/testers/
     * - Copying tester to student folder
     */
    private final String testerFile;
    
    /**
     * Student folder containing the file to grade
     * 
     * EXAMPLES:
     * - "Q1" (contains Q1a.java, Q1b.java)
     * - "Q2" (contains Q2a.java, Q2b.java)
     * - "Q3" (contains Q3.java, ShapeComparator.java)
     * 
     * USED FOR:
     * - Building path to student's submission
     * - Navigation in student's extracted folder
     */
    private final String studentFolder;
    
    /**
     * Student filename to grade
     * 
     * EXAMPLES:
     * - "Q1a.java"
     * - "Q1b.java"
     * - "Q3.java"
     * 
     * USED FOR:
     * - Checking if file exists
     * - Locating exact file to grade
     */
    private final String studentFile;
    
    // ================================================================
    // CONSTRUCTOR
    // ================================================================
    
    /**
     * Creates a GradingTask
     * 
     * EXAMPLE:
     * new GradingTask(
     *   "Q1a",              // questionId
     *   "Q1aTester.java",   // testerFile
     *   "Q1",               // studentFolder
     *   "Q1a.java"          // studentFile
     * )
     * 
     * This represents: Grade Q1a.java in folder Q1/ using Q1aTester.java
     * 
     * @param questionId Question identifier
     * @param testerFile Tester filename
     * @param studentFolder Folder containing student file
     * @param studentFile Student filename
     */
    public GradingTask(String questionId, String testerFile, 
                      String studentFolder, String studentFile) {
        this.questionId = questionId;
        this.testerFile = testerFile;
        this.studentFolder = studentFolder;
        this.studentFile = studentFile;
    }
    
    // ================================================================
    // GETTERS
    // ================================================================
    
    /**
     * Gets the question identifier
     * 
     * USED FOR:
     * - Grouping results by question
     * - Display formatting
     * - Matching with max scores
     * 
     * @return Question ID (e.g., "Q1a")
     */
    public String getQuestionId() {
        return questionId;
    }
    
    /**
     * Gets the tester filename
     * 
     * USED FOR:
     * - Locating tester in resources/input/testers/
     * - Copying tester to student folder
     * - Running tester class
     * 
     * @return Tester filename (e.g., "Q1aTester.java")
     */
    public String getTesterFile() {
        return testerFile;
    }
    
    /**
     * Gets the student folder name
     * 
     * USED FOR:
     * - Building path: data/extracted/{student}/{studentFolder}/
     * - Navigation in student's submission
     * 
     * @return Folder name (e.g., "Q1")
     */
    public String getStudentFolder() {
        return studentFolder;
    }
    
    /**
     * Gets the student filename
     * 
     * USED FOR:
     * - Checking if file exists
     * - Error messages
     * - Logging
     * 
     * @return Student filename (e.g., "Q1a.java")
     */
    public String getStudentFile() {
        return studentFile;
    }
    
    // ================================================================
    // HELPER METHODS
    // ================================================================
    
    /**
     * Gets tester class name (without .java extension)
     * 
     * CONVENIENCE METHOD:
     * - Removes ".java" from testerFile
     * - Used for running: java Q1aTester
     * 
     * EXAMPLE:
     * getTesterClassName() → "Q1aTester"
     * 
     * @return Tester class name without extension
     */
    public String getTesterClassName() {
        if (testerFile.endsWith(".java")) {
            return testerFile.substring(0, testerFile.length() - ".java".length());
        }
        return testerFile;
    }
    
    /**
     * Gets student class name (without .java extension)
     * 
     * CONVENIENCE METHOD:
     * - Removes ".java" from studentFile
     * - Used for checking .class files
     * 
     * EXAMPLE:
     * getStudentClassName() → "Q1a"
     * 
     * @return Student class name without extension
     */
    public String getStudentClassName() {
        if (studentFile.endsWith(".java")) {
            return studentFile.substring(0, studentFile.length() - ".java".length());
        }
        return studentFile;
    }
    
    // ================================================================
    // OBJECT METHODS
    // ================================================================
    
    /**
     * String representation for debugging
     * 
     * FORMAT:
     * GradingTask{questionId='Q1a', tester='Q1aTester.java', folder='Q1', file='Q1a.java'}
     * 
     * @return Human-readable representation
     */
    @Override
    public String toString() {
        return String.format(
            "GradingTask{questionId='%s', tester='%s', folder='%s', file='%s'}",
            questionId, testerFile, studentFolder, studentFile
        );
    }
    
    /**
     * Checks equality based on all fields
     * 
     * @param obj Object to compare with
     * @return true if equal, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        GradingTask other = (GradingTask) obj;
        
        return questionId.equals(other.questionId) &&
               testerFile.equals(other.testerFile) &&
               studentFolder.equals(other.studentFolder) &&
               studentFile.equals(other.studentFile);
    }
    
    /**
     * Generates hash code based on all fields
     * 
     * @return Hash code
     */
    @Override
    public int hashCode() {
        int result = questionId.hashCode();
        result = 31 * result + testerFile.hashCode();
        result = 31 * result + studentFolder.hashCode();
        result = 31 * result + studentFile.hashCode();
        return result;
    }
}