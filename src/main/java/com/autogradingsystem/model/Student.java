package com.autogradingsystem.model;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Represents a single student submission in the grading system.
 * This object links a Student ID to their specific folder on the file system.
 * 
 * @author IS442 Team
 * @version 2.0 (Updated for Phase 3 compatibility)
 */
public class Student {
    
    // The unique identifier for the student (e.g., "chee.teo.2022")
    // Used for reporting and logging.
    private String id;
    
    // The main folder containing ALL of this student's work.
    // Example: /tmp/mock_students/chee.teo.2022/
    // (Previous versions pointed directly to Q1, but now it points to the root
    // so we can access Q1, Q2, and Q3 dynamically).
    private Path rootPath; 

    // =========================================================================
    // CONSTRUCTORS
    // =========================================================================
    
    /**
     * Constructor with Path (original)
     * 
     * @param id Student username (e.g., "chee.teo.2022")
     * @param rootPath Path to student's root folder
     */
    public Student(String id, Path rootPath) {
        this.id = id;
        this.rootPath = rootPath;
    }
    
    /**
     * Constructor with String path (for compatibility)
     * 
     * @param id Student username (e.g., "chee.teo.2022")
     * @param folderPath String path to student's root folder
     */
    public Student(String id, String folderPath) {
        this.id = id;
        this.rootPath = Paths.get(folderPath);
    }

    // =========================================================================
    // GETTERS
    // =========================================================================
    
    /**
     * Get student ID
     * 
     * @return Student ID (username)
     */
    public String getId() { 
        return id; 
    }
    
    /**
     * Get student username (alias for getId for compatibility)
     * 
     * @return Student username
     */
    public String getUsername() {
        return id;
    }
    
    /**
     * Get root path to student's folder
     * 
     * @return Path to student's root folder
     */
    public Path getRootPath() {
        return rootPath;
    }
    
    // =========================================================================
    // PATH HELPERS
    // =========================================================================
    
    /**
     * DYNAMIC PATH GENERATOR
     * This method constructs the full path to a specific question folder.
     * 
     * How it works:
     * If rootPath is:      /tmp/mock_students/student1
     * And input is:        "Q1"
     * The result becomes:  /tmp/mock_students/student1/Q1
     * 
     * @param questionFolder The name of the sub-folder (e.g., "Q1", "Q2")
     * @return The absolute path to that specific question.
     */
    public Path getQuestionPath(String questionFolder) {
        // .resolve() is a smart Java method that handles "/" automatically
        // regardless of whether you are on Windows or Mac.
        return rootPath.resolve(questionFolder);
    }
    
    // =========================================================================
    // UTILITY METHODS
    // =========================================================================
    
    @Override
    public String toString() {
        return "Student{id='" + id + "', rootPath=" + rootPath + "}";
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Student other = (Student) obj;
        return id.equals(other.id);
    }
    
    @Override
    public int hashCode() {
        return id.hashCode();
    }
}