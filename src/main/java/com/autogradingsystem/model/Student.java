package com.autogradingsystem.model;

import com.autogradingsystem.PathConfig;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Student - Represents a Student and Their Submission Location
 * 
 * PURPOSE:
 * - Encapsulates student information and folder location
 * - Provides convenient path building methods
 * - Shared across all services
 * - Immutable for thread safety
 * 
 * CREATED BY:
 * - ExecutionController when loading students
 * 
 * USED BY:
 * - ExecutionController for grading
 * - GradingResult for storing student info
 * 
 * CHANGES FROM v3.0:
 * - Updated getQuestionPath() to use PathConfig
 * - Added comprehensive JavaDoc
 * - Clarified constructor purposes
 * 
 * @author IS442 Team
 * @version 4.0 (Spring Boot Microservices Structure)
 */
public class Student {
    
    // ================================================================
    // FIELDS
    // ================================================================
    
    private String id;                                          // mutable — identity may be resolved from header
private final Path rootPath;
private boolean folderRenamed = true;
private List<String> missingHeaderFiles = new ArrayList<>();
private boolean anomaly = false;
private String rawFolderName = null;
    
    // ================================================================
    // CONSTRUCTORS
    // ================================================================
    
    /**
     * Constructor with Path (Primary)
     * 
     * USE CASE:
     * - When you already have a Path object
     * - Most common in modern code
     * 
     * EXAMPLE:
     * Path studentPath = Paths.get("data", "extracted", "chee.teo.2022");
     * Student student = new Student("chee.teo.2022", studentPath);
     * 
     * @param id Student username (e.g., "chee.teo.2022")
     * @param rootPath Path to student's root folder
     */
    public Student(String id, Path rootPath) {
        this.id = id;
        this.rootPath = rootPath;
    }
    
    /**
     * Constructor with String path (Convenience)
     * 
     * USE CASE:
     * - When you have path as String
     * - Compatibility with string-based APIs
     * - Legacy code support
     * 
     * INTERNALLY:
     * - Converts String to Path using Paths.get()
     * - Then delegates to primary constructor
     * 
     * EXAMPLE:
     * String folderPath = "data/extracted/chee.teo.2022";
     * Student student = new Student("chee.teo.2022", folderPath);
     * 
     * WHY TWO CONSTRUCTORS?
     * - Flexibility: Accept both String and Path inputs
     * - Both create the same Student object
     * - Choose based on what you have available
     * 
     * @param id Student username (e.g., "chee.teo.2022")
     * @param folderPath String path to student's root folder
     */
    public Student(String id, String folderPath) {
        this.id = id;
        this.rootPath = Paths.get(folderPath);
    }
    
    // ================================================================
    // GETTERS
    // ================================================================
    
    /**
     * Gets the student's username (primary identifier)
     * 
     * USED FOR:
     * - Identifying student in results
     * - Grouping results by student
     * - Display formatting
     * 
     * @return Student username (e.g., "ping.lee.2023")
     */
    public String getId() {
        return id;
    }
    
    /**
     * Gets the student's username (alias for getId)
     * 
     * SEMANTIC CLARITY:
     * - Same as getId() but more explicit
     * - Use when context implies "username" meaning
     * 
     * EXAMPLE:
     * String name = student.getUsername();  // Clear intent
     * String name = student.getId();        // Also correct
     * 
     * @return Student username (e.g., "ping.lee.2023")
     */
    public String getUsername() {
        return id;
    }
    
    /**
     * Gets the path to student's root folder
     * 
     * RETURNS:
     * Path object pointing to: data/extracted/{username}/
     * 
     * @return Path to student's root folder
     */
    public Path getRootPath() {
        return rootPath;
    }
    
    // ================================================================
    // HELPER METHODS
    // ================================================================
    
    /**
     * Builds path to a specific question folder for this student
     * 
     * USES PathConfig:
     * - Ensures consistent path building across system
     * - Centralized path management
     * 
     * EXAMPLE:
     * student.getQuestionPath("Q1")
     * → data/extracted/ping.lee.2023/Q1/
     * 
     * UPDATED IN v4.0:
     * - Now uses PathConfig.getStudentQuestionFolder()
     * - Consistent with centralized path management
     * 
     * @param questionFolder Question folder name (e.g., "Q1")
     * @return Path to student's question folder
     */
    public Path getQuestionPath(String questionFolder) {
        return PathConfig.getStudentQuestionFolder(this.id, questionFolder);
    }
    public boolean isFolderRenamed() { return folderRenamed; }
public void setFolderRenamed(boolean folderRenamed) { this.folderRenamed = folderRenamed; }

public List<String> getMissingHeaderFiles() { return missingHeaderFiles; }
public void setMissingHeaderFiles(List<String> missingHeaderFiles) { this.missingHeaderFiles = missingHeaderFiles; }

public boolean isAnomaly() { return anomaly; }
public void setAnomaly(boolean anomaly) { this.anomaly = anomaly; }

public String getRawFolderName() { return rawFolderName; }
public void setRawFolderName(String rawFolderName) { this.rawFolderName = rawFolderName; }

// Needed for identity resolution from header email
public void setId(String id) { this.id = id; }
    
    // ================================================================
    // OBJECT METHODS
    // ================================================================
    
    /**
     * String representation for debugging
     * 
     * FORMAT:
     * Student{id='ping.lee.2023', path=data/extracted/ping.lee.2023}
     * 
     * @return Human-readable representation
     */
    @Override
    public String toString() {
        return String.format(
            "Student{id='%s', path=%s}",
            id,
            rootPath
        );
    }
    
    /**
     * Checks equality based on student ID
     * 
     * NOTE:
     * - Two students are equal if they have the same ID
     * - Path doesn't matter for equality
     * - Same student extracted to different locations = still same student
     * 
     * @param obj Object to compare with
     * @return true if same student ID, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        Student other = (Student) obj;
        return id.equals(other.id);
    }
    
    /**
     * Generates hash code based on student ID
     * 
     * CONSISTENT WITH equals():
     * - Only uses ID for hash code
     * - Same student ID = same hash code
     * 
     * @return Hash code
     */
    @Override
    public int hashCode() {
        return id.hashCode();
    }
}