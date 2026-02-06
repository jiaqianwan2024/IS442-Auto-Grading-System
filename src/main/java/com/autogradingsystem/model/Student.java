package com.autogradingsystem.model;

import java.nio.file.Path;

/**
 * Represents a single student submission in the grading system.
 * This object links a Student ID to their specific folder on the file system.
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

    // Constructor: Initializes the object with specific data.
    public Student(String id, Path rootPath) {
        this.id = id;
        this.rootPath = rootPath;
    }

    // Getter for the ID (used by the Controller to print logs)
    public String getId() { 
        return id; 
    }
    
    /**
     * DYNAMIC PATH GENERATOR
     * This method constructs the full path to a specific question folder.
     * * How it works:
     * If rootPath is:      /tmp/mock_students/student1
     * And input is:        "Q1"
     * The result becomes:  /tmp/mock_students/student1/Q1
     * * @param questionFolder The name of the sub-folder (e.g., "Q1", "Q2")
     * @return The absolute path to that specific question.
     */
    public Path getQuestionPath(String questionFolder) {
        // .resolve() is a smart Java method that handles "/" automatically
        // regardless of whether you are on Windows or Mac.
        return rootPath.resolve(questionFolder);
    }
}