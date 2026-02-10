package com.autogradingsystem.execution.controller;

import com.autogradingsystem.PathConfig;
import com.autogradingsystem.execution.service.TesterInjector;
import com.autogradingsystem.execution.service.CompilerService;
import com.autogradingsystem.execution.service.ProcessRunner;
import com.autogradingsystem.execution.service.OutputParser;

import com.autogradingsystem.model.Student;
import com.autogradingsystem.model.GradingTask;
import com.autogradingsystem.model.GradingPlan;
import com.autogradingsystem.model.GradingResult;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * ExecutionController - Brain for Execution Service (Phase 3)
 * 
 * PURPOSE:
 * - Coordinates grading execution workflow
 * - Acts as entry point for execution service
 * - Called by Main.java during grading phase
 * 
 * RESPONSIBILITIES:
 * - Load all students from extracted directory
 * - Execute grading for each student and task
 * - Compile student code + testers
 * - Run testers and capture output
 * - Parse scores and create results
 * 
 * @author IS442 Team
 * @version 4.0
 */
public class ExecutionController {
    
    private final TesterInjector testerInjector;
    private final CompilerService compilerService;
    private final ProcessRunner processRunner;
    private final OutputParser outputParser;
    
    /**
     * Constructor - initializes execution services
     */
    public ExecutionController() {
        this.testerInjector = new TesterInjector();
        this.compilerService = new CompilerService();
        this.processRunner = new ProcessRunner();
        this.outputParser = new OutputParser();
    }
    
    /**
     * Executes grading for all students using the provided grading plan
     * 
     * WORKFLOW:
     * 1. Load all students from OUTPUT_EXTRACTED directory
     * 2. For each student:
     *    - For each task in grading plan:
     *      - Grade the task
     *      - Collect result
     * 3. Return all results
     * 
     * LOGGING:
     * - Provides detailed progress output for each student
     * - Shows compilation status and scores
     * - This detailed output is kept for debugging value
     * 
     * @param plan GradingPlan containing tasks to execute
     * @return List of all GradingResult objects
     * @throws IOException if grading fails
     */
    public List<GradingResult> gradeAllStudents(GradingPlan plan) throws IOException {
        
        // Load students
        List<Student> students = loadStudents();
        
        if (students.isEmpty()) {
            throw new IOException("No students found in: " + PathConfig.OUTPUT_EXTRACTED);
        }
        
        List<GradingResult> allResults = new ArrayList<>();
        int studentCount = 0;
        
        // Grade each student
        for (Student student : students) {
            studentCount++;
            
            System.out.println("\n👤 [" + studentCount + "/" + students.size() + "] " + 
                             student.getId());
            
            // Grade each task for this student
            for (GradingTask task : plan.getTasks()) {
                GradingResult result = gradeTask(student, task);
                allResults.add(result);
                logTaskResult(task, result);
            }
        }
        
        return allResults;
    }
    
    /**
     * Grades a single task for a single student
     * 
     * WORKFLOW:
     * 1. Check if student file exists (.java or .class)
     * 2. Copy tester to student's question folder
     * 3. Compile all .java files in folder
     * 4. Run tester class
     * 5. Parse score from output
     * 6. Create and return GradingResult
     * 
     * @param student Student being graded
     * @param task Task to grade
     * @return GradingResult with score, output, and status
     * @throws IOException if grading fails
     */
    private GradingResult gradeTask(Student student, GradingTask task) throws IOException {
        
        // Build paths
        Path studentQuestionFolder = student.getQuestionPath(task.getStudentFolder());
        Path studentFile = studentQuestionFolder.resolve(task.getStudentFile());
        
        // Check if student file exists
        boolean hasJavaFile = Files.exists(studentFile);
        boolean hasClassFile = false;
        Path classFile = null;
        
        if (!hasJavaFile) {
            // Fallback: check for .class file
            String className = task.getStudentFile().replace(".java", ".class");
            classFile = studentQuestionFolder.resolve(className);
            hasClassFile = Files.exists(classFile);
            
            if (!hasClassFile) {
                // File not found - build detailed error message
                String errorMessage = buildFileNotFoundError(
                    task.getStudentFile(), 
                    studentQuestionFolder
                );
                
                return new GradingResult(
                    student, task, 0.0, errorMessage, "FILE_NOT_FOUND"
                );
            }
        }
        
        // Copy tester to student folder
        try {
            testerInjector.copyTester(task.getTesterFile(), studentQuestionFolder);
        } catch (IOException e) {
            return new GradingResult(
                student, task, 0.0,
                "Failed to copy tester: " + e.getMessage(),
                "TESTER_COPY_FAILED"
            );
        }
        
        // Compile all .java files
        boolean compiled = compilerService.compile(studentQuestionFolder);
        
        if (!compiled) {
            return new GradingResult(
                student, task, 0.0,
                "Compilation failed - check syntax errors",
                "COMPILATION_FAILED"
            );
        }
        
        // Run tester
        String testerClassName = task.getTesterFile().replace(".java", "");
        String output = processRunner.runTester(testerClassName, studentQuestionFolder);
        
        // Parse score
        double score = outputParser.parseScore(output);
        
        // Create and return result
        return new GradingResult(student, task, score, output);
    }
    
    /**
     * Loads all students from OUTPUT_EXTRACTED directory
     * 
     * @return List of Student objects
     * @throws IOException if directory cannot be read
     */
    private List<Student> loadStudents() throws IOException {
        
        List<Student> students = new ArrayList<>();
        
        if (!Files.exists(PathConfig.OUTPUT_EXTRACTED)) {
            return students;
        }
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(PathConfig.OUTPUT_EXTRACTED)) {
            for (Path studentFolder : stream) {
                if (Files.isDirectory(studentFolder)) {
                    String username = studentFolder.getFileName().toString();
                    students.add(new Student(username, studentFolder));
                }
            }
        }
        
        return students;
    }
    
    /**
     * Logs the result of grading a single task
     * Provides detailed output for debugging
     * 
     * @param task Task that was graded
     * @param result Result of grading
     */
    private void logTaskResult(GradingTask task, GradingResult result) {
        
        String statusSymbol;
        String statusText;
        
        switch (result.getStatus()) {
            case "PERFECT":
                statusSymbol = "✅";
                statusText = "PERFECT";
                break;
            case "PARTIAL":
                statusSymbol = "⚠️ ";
                statusText = "PARTIAL";
                break;
            case "FAILED":
                statusSymbol = "❌";
                statusText = "FAILED";
                break;
            case "COMPILATION_FAILED":
                statusSymbol = "❌";
                statusText = "COMPILATION_FAILED";
                break;
            case "FILE_NOT_FOUND":
                statusSymbol = "❌";
                statusText = "FILE_NOT_FOUND";
                break;
            default:
                statusSymbol = "ℹ️ ";
                statusText = result.getStatus();
        }
        
        System.out.println("   📝 " + task.getQuestionId() + "... " + 
                         statusSymbol + " " + result.getScore() + " points (" + statusText + ")");
    }
    
    /**
     * Builds detailed error message when file not found
     * 
     * @param expectedFile Expected filename
     * @param folder Folder where file should be
     * @return Detailed error message
     */
    private String buildFileNotFoundError(String expectedFile, Path folder) {
        
        StringBuilder error = new StringBuilder();
        error.append("File not found: ").append(expectedFile).append("\n");
        error.append("Expected location: ").append(folder.toAbsolutePath()).append("\n");
        
        try {
            if (Files.exists(folder)) {
                error.append("Folder contents: ");
                
                List<String> items = new ArrayList<>();
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
                    for (Path item : stream) {
                        items.add(item.getFileName().toString());
                    }
                }
                
                if (items.isEmpty()) {
                    error.append("[Empty folder]");
                } else {
                    error.append(String.join(", ", items));
                    
                    // Check if there's a wrapper folder
                    for (String item : items) {
                        if (!item.startsWith("Q") && !item.startsWith(".")) {
                            error.append("\n⚠️  Potential wrapper folder detected: ").append(item);
                            error.append("\n   (Wrapper flattening may have failed)");
                            break;
                        }
                    }
                }
            } else {
                error.append("[Folder does not exist]");
            }
        } catch (IOException e) {
            error.append("[Could not read folder]");
        }
        
        return error.toString();
    }
}