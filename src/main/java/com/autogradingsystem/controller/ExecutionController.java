package com.autogradingsystem.controller;

// specific imports for the services we built (Injector, Compiler, Runner, Parser)
import com.autogradingsystem.model.Student;
import com.autogradingsystem.service.execution.CompilerService;
import com.autogradingsystem.service.execution.ProcessRunner;
import com.autogradingsystem.service.execution.TesterInjector;
import com.autogradingsystem.service.grading.OutputParser;

// standard Java file handling and list imports
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ExecutionController {

    // Instantiate the "Worker" services.
    // These tools handle the specific low-level tasks.
    private TesterInjector injector = new TesterInjector();
    private CompilerService compiler = new CompilerService();
    private ProcessRunner runner = new ProcessRunner();
    private OutputParser parser = new OutputParser();

    // ---------------------------------------------------------
    // HARDCODED CONFIGURATION
    // ---------------------------------------------------------
    // 1. DEFINE SPECIFIC TASKS
    // This array defines exactly which tests will be run and in what order.
    // It is used to generate the columns in the final report (Q1A, Q1B, etc.)
    private static final String[] GRADING_TASKS = {"Q1A", "Q1B", "Q2A", "Q2B", "Q3"};

    // ---------------------------------------------------------
    // DATA LOADING (MOCK)
    // ---------------------------------------------------------
    // 2. MOCK LOADER
    // In a real system, this would read from a Database or CSV file.
    // Here, we manually create Student objects pointing to specific folders on your Mac.
    public List<Student> getMockStudents() {
        List<Student> students = new ArrayList<>();
        // Creating students with their ID and the Path to their submission folder.
        // NOTE: These paths are absolute paths on your specific machine.
        students.add(new Student("chee.teo.2022", Paths.get("/tmp/mock_students/chee.teo.2022")));
        students.add(new Student("david.2024",    Paths.get("/tmp/mock_students/david.2024")));
        students.add(new Student("fen.lai.2022",  Paths.get("/tmp/mock_students/fen.lai.2022")));
        students.add(new Student("jing.lim.2021", Paths.get("/tmp/mock_students/jing.lim.2021")));
        students.add(new Student("ping.lee.2023", Paths.get("/tmp/mock_students/ping.lee.2023")));
        students.add(new Student("xing.yan.2023", Paths.get("/tmp/mock_students/xing.yan.2023")));
        return students;
    }

    // ---------------------------------------------------------
    // CORE LOGIC ENGINE
    // ---------------------------------------------------------
    // 3. MAIN LOGIC LOOP
    // This is the "Heart" of the program. It ties everything together.
    public void runGrading() {
        List<Student> students = getMockStudents();

        // OUTER LOOP: Go through every student one by one
        for (Student s : students) {
            System.out.println("==========================================");
            System.out.println("--- Grading Student: " + s.getId() + " ---");
            System.out.println("==========================================");

            double totalScore = 0;
            StringBuilder scoreSummary = new StringBuilder(); // Builds the string: "Q1A: 3.0  Q1B: 0.0"

            // INNER LOOP: For the current student, go through every task (Q1A, Q1B, Q2A...)
            for (String task : GRADING_TASKS) {
                
                // DYNAMIC MAPPING:
                // We need to know:
                // 1. Which folder is this task in? (Q1A is inside the "Q1" folder)
                // 2. Which Tester file grades this task? (Q1A needs "Q1aTester.java")
                String folderName = getFolderForTask(task);
                String testerName = getTesterForTask(task);
                
                // Combine student path + question folder (e.g., /tmp/.../student1/Q1)
                Path fullPath = s.getQuestionPath(folderName);

                // Validation: Check if the student actually submitted the folder
                if (!Files.exists(fullPath)) {
                    System.out.println("   [Error] Folder not found: " + fullPath);
                    scoreSummary.append(task).append(":0.0  ");
                    continue; // Skip to next task, score is 0
                }

                try {
                    // STEP A: INJECT
                    // Copy the Teacher's Tester file into the Student's folder
                    injector.copyTester(testerName, fullPath);

                    // STEP B: COMPILE
                    // Run 'javac *.java' in that folder
                    boolean compileSuccess = compiler.compile(fullPath);

                    if (compileSuccess) {
                        // STEP C: RUN
                        // Execute 'java Q1aTester' and capture the text output
                        String className = testerName.replace(".java", "");
                        String output = runner.runTester(className, fullPath);
                        
                        // STEP D: PARSE
                        // Read the text output to find the number at the bottom
                        double score = parser.parseScore(output);
                        
                        // Log progress to console
                        System.out.println("   Processed " + task + " -> Score: " + score);
                        
                        // Add to totals
                        totalScore += score;
                        scoreSummary.append(task).append(":").append(score).append("  ");
                    } else {
                        // If compile fails, automatically give 0
                        System.out.println("   " + task + " Compilation Failed.");
                        scoreSummary.append(task).append(":0.0  ");
                    }

                } catch (Exception e) {
                    // General error handler (e.g., File permission issues)
                    System.out.println("   [Error] " + task + ": " + e.getMessage());
                    scoreSummary.append(task).append(":0.0  ");
                }
            }
            // Final Report Line for this student (matches Ground Truth requirement)
            System.out.println("\nRESULT: " + s.getId() + ": " + scoreSummary.toString() + "Total: " + totalScore + "\n");
        }
    }

    // ---------------------------------------------------------
    // HELPER METHODS (MAPPING LOGIC)
    // ---------------------------------------------------------
    
    // Helper 1: Decides which folder a task belongs to.
    // Logic: Q1A and Q1B both live in the "Q1" folder.
    private String getFolderForTask(String task) {
        if (task.startsWith("Q1")) return "Q1"; 
        if (task.startsWith("Q2")) return "Q2"; 
        if (task.startsWith("Q3")) return "Q3";
        return "Unknown";
    }

    // Helper 2: Decides which Tester file to inject.
    // Logic: Maps the task ID "Q1A" to the filename "Q1aTester.java"
    private String getTesterForTask(String task) {
        switch (task) {
            case "Q1A": return "Q1aTester.java";
            case "Q1B": return "Q1bTester.java";
            case "Q2A": return "Q2aTester.java";
            case "Q2B": return "Q2bTester.java";
            case "Q3":  return "Q3Tester.java";
            default: return "UnknownTester.java";
        }
    }
}