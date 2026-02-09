package com.autogradingsystem.controller;

import com.autogradingsystem.discovery.GradingPlanBuilder;
import com.autogradingsystem.discovery.TemplateDiscovery;
import com.autogradingsystem.discovery.TesterDiscovery;
import com.autogradingsystem.model.*;
import com.autogradingsystem.service.execution.CompilerService;
import com.autogradingsystem.service.execution.ProcessRunner;
import com.autogradingsystem.service.execution.TesterInjector;
import com.autogradingsystem.service.file.UnzipService;
import com.autogradingsystem.service.grading.OutputParser;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * ExecutionController - Main Orchestrator for All Phases
 * 
 * COMPLETE WORKFLOW:
 * 1. Phase 1: Extract and validate student submissions
 * 2. Phase 2: Discover exam structure and build grading plan
 * 3. Phase 3: Execute grading for all students
 * 4. Phase 4: Generate reports (future)
 * 
 * @author IS442 Team
 * @version 3.0 (Phase 3 Complete)
 */
public class ExecutionController {
    
    // =========================================================================
    // PHASE 2: DISCOVERY COMPONENTS
    // =========================================================================
    
    private UnzipService unzipService;
    private TemplateDiscovery templateDiscovery;
    private TesterDiscovery testerDiscovery;
    private GradingPlanBuilder planBuilder;
    
    // =========================================================================
    // PHASE 3: GRADING EXECUTION COMPONENTS
    // =========================================================================
    
    private TesterInjector testerInjector;
    private CompilerService compilerService;
    private ProcessRunner processRunner;
    private OutputParser outputParser;
    
    /**
     * Constructor - Initialize all components
     */
    public ExecutionController() {
        // Phase 2: Discovery components
        this.unzipService = new UnzipService();
        this.templateDiscovery = new TemplateDiscovery();
        this.testerDiscovery = new TesterDiscovery();
        this.planBuilder = new GradingPlanBuilder();
        
        // Phase 3: Grading components
        this.testerInjector = new TesterInjector();
        this.compilerService = new CompilerService();
        this.processRunner = new ProcessRunner();
        this.outputParser = new OutputParser();
    }
    
    // =========================================================================
    // PHASE 1 + PHASE 2: INITIALIZE
    // =========================================================================
    
    /**
     * Initialize system - Run Phase 1 + Phase 2
     * 
     * WORKFLOW:
     * 1. Phase 1: Extract and validate student submissions
     * 2. Phase 2: Discover exam structure from template
     * 3. Phase 2: Discover tester files
     * 4. Phase 2: Build grading plan
     * 5. Return grading plan (ready for Phase 3)
     * 
     * @return GradingPlan ready for execution
     * @throws IOException if files cannot be read or discovered
     */
    public GradingPlan initialize() throws IOException {
        
        System.out.println("\n" + "=".repeat(70));
        System.out.println("🚀 INITIALIZING AUTO-GRADING SYSTEM");
        System.out.println("=".repeat(70));
        
        // =====================================================================
        // PHASE 1: EXTRACTION & VALIDATION
        // =====================================================================
        
        System.out.println("\n=== PHASE 1: EXTRACTION & VALIDATION ===");
        unzipService.extractAndValidateStudents();
        System.out.println("✅ Phase 1 complete - Students extracted to data/extracted/");
        
        // =====================================================================
        // PHASE 2: DISCOVERY & PLANNING
        // =====================================================================
        
        System.out.println("\n=== PHASE 2: DISCOVERY & PLANNING ===");
        
        // Find template ZIP
        Path templateZip = findTemplateZip();
        
        // Discover exam structure from template
        ExamStructure structure = templateDiscovery.discoverStructure(templateZip);
        
        // Discover tester files
        Path testersDir = Paths.get("src", "main", "resources", "testers");
        TesterMap testers = testerDiscovery.discoverTesters(testersDir);
        
        // Build grading plan
        GradingPlan plan = planBuilder.buildPlan(structure, testers);
        
        // =====================================================================
        // SUMMARY
        // =====================================================================
        
        System.out.println("\n" + "=".repeat(70));
        System.out.println("✅ INITIALIZATION COMPLETE");
        System.out.println("=".repeat(70));
        System.out.println("Grading plan ready: " + plan.getSummary());
        
        // Warn if any testers are missing
        if (plan.getUngradableTaskCount() > 0) {
            System.err.println("\n⚠️  WARNING: " + plan.getUngradableTaskCount() + 
                             " task(s) missing testers - these will be skipped");
        }
        
        System.out.println("=".repeat(70) + "\n");
        
        return plan;
    }
    
    /**
     * Helper method: Find template ZIP file
     * 
     * @return Path to template ZIP
     * @throws IOException if template not found
     */
    private Path findTemplateZip() throws IOException {
        
        Path templateDir = Paths.get("data", "input", "template");
        
        // Check if directory exists
        if (!Files.exists(templateDir)) {
            throw new IOException(
                "Template directory not found: " + templateDir + "\n" +
                "Please create directory and place template ZIP there"
            );
        }
        
        // Find all ZIP files
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(templateDir, "*.zip")) {
            
            Path firstZip = null;
            int count = 0;
            
            for (Path zip : stream) {
                if (firstZip == null) {
                    firstZip = zip;
                }
                count++;
            }
            
            if (firstZip == null) {
                throw new IOException(
                    "No ZIP files found in: " + templateDir + "\n" +
                    "Please place template ZIP (e.g., RenameToYourUsername.zip) there"
                );
            }
            
            if (count > 1) {
                System.out.println("   ⚠️  Multiple template ZIPs found, using: " + firstZip.getFileName());
            }
            
            return firstZip;
        }
    }
    
    // =========================================================================
    // PHASE 3: GRADING EXECUTION
    // =========================================================================
    
    /**
     * Run grading with plan - Phase 3
     * 
     * WORKFLOW:
     * 1. Get students from data/extracted/ folder
     * 2. For each student:
     *    a. For each task in grading plan:
     *       - Skip if no tester (helper files)
     *       - Copy tester to student folder
     *       - Compile student code + tester
     *       - Run tester
     *       - Parse score from output
     *       - Store result
     * 3. Return all results
     * 
     * @param plan GradingPlan from initialize()
     * @return List of GradingResult objects
     * @throws IOException if grading fails catastrophically
     */
    public List<GradingResult> runGrading(GradingPlan plan) throws IOException {
        
        System.out.println("\n" + "=".repeat(70));
        System.out.println("🎯 PHASE 3: GRADING EXECUTION");
        System.out.println("=".repeat(70));
        
        // Get students from filesystem
        List<Student> students = getStudentsFromFilesystem();
        System.out.println("\n📋 Grading " + students.size() + " student(s) on " + 
                         plan.getGradableTaskCount() + " task(s)\n");
        
        // Initialize results collection
        List<GradingResult> allResults = new ArrayList<>();
        
        // Grade each student
        int studentNum = 1;
        for (Student student : students) {
            System.out.println("─".repeat(70));
            System.out.println("👤 [" + studentNum + "/" + students.size() + "] " + 
                             student.getUsername());
            System.out.println("─".repeat(70));
            
            // Grade each task for this student
            for (GradingTask task : plan.getTasks()) {
                
                // Skip helper files (no tester)
                if (!task.hasTester()) {
                    System.out.println("   ⏭️  Skipping " + task.getQuestionId() + 
                                     " (helper file - no tester)");
                    continue;
                }
                
                System.out.print("   📝 " + task.getQuestionId() + "... ");
                
                try {
                    // Grade this task
                    GradingResult result = gradeTask(student, task);
                    allResults.add(result);
                    
                    // Display result
                    if (result.isSuccessful()) {
                        System.out.println("✅ " + String.format("%.1f", result.getScore()) + 
                                         " points (" + result.getStatus() + ")");
                    } else {
                        System.out.println("❌ " + result.getStatus());
                    }
                    
                } catch (Exception e) {
                    // Handle errors gracefully - create error result
                    GradingResult errorResult = new GradingResult(
                        student, task, 0.0, "Error: " + e.getMessage(), "ERROR"
                    );
                    allResults.add(errorResult);
                    System.out.println("❌ ERROR: " + e.getMessage());
                }
            }
            
            System.out.println();
            studentNum++;
        }
        
        // Final summary
        System.out.println("=".repeat(70));
        System.out.println("✅ PHASE 3 COMPLETE");
        System.out.println("=".repeat(70));
        System.out.println("Total results: " + allResults.size());
        System.out.println("Successful: " + countSuccessful(allResults));
        System.out.println("Failed: " + countFailed(allResults));
        System.out.println("=".repeat(70) + "\n");
        
        return allResults;
    }
    
    /**
     * Grade a single task for a student
     * 
     * WORKFLOW:
     * 1. Build paths to student file and tester file
     * 2. Check student file exists (FIX 4: also check for .class files)
     * 3. Copy tester to student folder
     * 4. Compile all Java files in folder
     * 5. Run tester class
     * 6. Parse score from output
     * 7. Return result
     * 
     * @param student Student to grade
     * @param task Task to grade
     * @return GradingResult with score and output
     * @throws IOException if file operations fail
     */
    private GradingResult gradeTask(Student student, GradingTask task) throws IOException {
        
        // Build path to student's question folder using Student's helper method
        Path studentQuestionFolder = student.getQuestionPath(task.getStudentFolder());
        
        // Build path to student's Java file
        Path studentFile = studentQuestionFolder.resolve(task.getStudentFile());
        
        // FIX 4 & 5: Enhanced file existence checking with better error messages
        boolean hasJavaFile = Files.exists(studentFile);
        boolean hasClassFile = false;
        Path classFile = null;
        
        if (!hasJavaFile) {
            // FIX 4: Check if .class file exists as fallback
            String className = task.getStudentFile().replace(".java", ".class");
            classFile = studentQuestionFolder.resolve(className);
            hasClassFile = Files.exists(classFile);
            
            if (!hasClassFile) {
                // FIX 5: Better error message with folder contents
                String errorMessage = buildFileNotFoundError(
                    task.getStudentFile(), 
                    studentQuestionFolder
                );
                
                return new GradingResult(
                    student, task, 0.0, errorMessage, "FILE_NOT_FOUND"
                );
            }
        }
        
        // Copy tester to student's folder
        try {
            testerInjector.copyTester(task.getTesterFile(), studentQuestionFolder);
        } catch (IOException e) {
            return new GradingResult(
                student, task, 0.0,
                "Failed to copy tester: " + e.getMessage(),
                "TESTER_COPY_FAILED"
            );
        }
        
        // Compile
        // If student only has .class file, we still need to compile the tester
        boolean compiled = compilerService.compile(studentQuestionFolder);
        
        if (!compiled) {
            return new GradingResult(
                student, task, 0.0,
                "Compilation failed - check syntax errors",
                "COMPILATION_FAILED"
            );
        }
        
        // Run the tester class
        String testerClassName = task.getTesterFile().replace(".java", "");
        String output = processRunner.runTester(testerClassName, studentQuestionFolder);
        
        // Parse score from output
        double score = outputParser.parseScore(output);
        
        // Create and return result
        return new GradingResult(student, task, score, output);
    }
    
    /**
     * FIX 5: Builds detailed error message when file not found
     * 
     * Shows:
     * - Expected file path
     * - Actual folder contents
     * - Hints about wrapper folders
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
                            error.append("\n   (Phase 1 wrapper flattening may have failed)");
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
    
    /**
     * Get students from extracted folders
     * 
     * Scans data/extracted/ for student folders
     * Each folder name is a student username
     * 
     * @return List of Student objects
     * @throws IOException if directory cannot be read
     */
    private List<Student> getStudentsFromFilesystem() throws IOException {
        List<Student> students = new ArrayList<>();
        Path extractedDir = Paths.get("data", "extracted");
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(extractedDir)) {
            for (Path studentFolder : stream) {
                if (Files.isDirectory(studentFolder)) {
                    String username = studentFolder.getFileName().toString();
                    students.add(new Student(username, studentFolder));
                }
            }
        }
        
        // Sort alphabetically for consistent output
        students.sort((a, b) -> a.getUsername().compareTo(b.getUsername()));
        
        return students;
    }
    
    /**
     * Count successful results
     */
    private int countSuccessful(List<GradingResult> results) {
        return (int) results.stream().filter(GradingResult::isSuccessful).count();
    }
    
    /**
     * Count failed results
     */
    private int countFailed(List<GradingResult> results) {
        return (int) results.stream().filter(r -> !r.isSuccessful()).count();
    }
}