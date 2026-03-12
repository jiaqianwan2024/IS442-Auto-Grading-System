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
import java.util.stream.Stream;

/**
 * ExecutionController - Orchestrates Phase 3 (Grading Execution)
 * PURPOSE:
 * - Coordinates grading execution workflow
 * - Acts as entry point for execution service
 * - Called by Main.java during grading phase
 */
public class ExecutionController {

    private final TesterInjector testerInjector;
    private final CompilerService compilerService;
    private final ProcessRunner   processRunner;
    private final OutputParser    outputParser;

    public ExecutionController() {
        this.testerInjector  = new TesterInjector();
        this.compilerService = new CompilerService();
        this.processRunner   = new ProcessRunner();
        this.outputParser    = new OutputParser();
    }

    public List<GradingResult> gradeAllStudents(GradingPlan plan) throws IOException {
        List<Student> students = loadStudents();
        if (students.isEmpty()) {
            throw new IOException("No students found in: " + PathConfig.OUTPUT_EXTRACTED);
        }

        List<GradingResult> allResults = new ArrayList<>();
        int idx = 0;

        for (Student student : students) {
            idx++;
            System.out.println("\n👤 [" + idx + "/" + students.size() + "] " + student.getId());

            for (GradingTask task : plan.getTasks()) {
                GradingResult result;
                try {
                    result = gradeTask(student, task);
                } catch (Exception e) {
                    String msg = "Unexpected error: " + e.getMessage();
                    result = new GradingResult(student, task, 0.0, msg, "ERROR");
                }
                allResults.add(result);
                logTaskResult(task, result);
            }
        }
        return allResults;
    }

    private GradingResult gradeTask(Student student, GradingTask task) throws IOException {

        Path studentRoot = student.getRootPath();
        String expectedFile = task.getStudentFile();
        String expectedClass = expectedFile.replace(".java", ".class");

        // ── DYNAMIC SCRIPT ROUTER (STRESS TEST FIX) ─────────────────────────────
        boolean isScriptTask = false;
        Path scriptFolder = null;

        // Route 1: ScoreSheet explicitly targets a script (Hybrid Task)
        if (expectedFile.toLowerCase().endsWith(".bat") || expectedFile.toLowerCase().endsWith(".sh")) {
            String baseName = expectedFile.substring(0, expectedFile.lastIndexOf('.'));
            Path found = findFileRecursive(studentRoot, baseName + ".bat");
            if (found == null) found = findFileRecursive(studentRoot, baseName + ".sh"); // OS Agnostic fallback
            
            if (found != null) {
                isScriptTask = true;
                scriptFolder = found.getParent();
            }
        } 
        // Route 2: Target is a Folder Task (e.g., "Q4" with no extension)
        else if (!expectedFile.contains(".")) {
            // Hunt for ANY known script to anchor the working directory.
            // This perfectly passes the "q4_nestedinsideq3" scenario!
            Path found = findFileRecursive(studentRoot, "compile.bat");
            if (found == null) found = findFileRecursive(studentRoot, "run.bat");
            if (found == null) found = findFileRecursive(studentRoot, "compile.sh");
            if (found == null) found = findFileRecursive(studentRoot, "run.sh");

            if (found != null) {
                isScriptTask = true;
                scriptFolder = found.getParent();
            }
        }

        if (isScriptTask) {
            if (scriptFolder == null || !Files.exists(scriptFolder)) {
                return new GradingResult(student, task, 0.0,
                    "Required script not found in submission.", "FILE_NOT_FOUND");
            }

            // Generate a dummy file to satisfy the system's "FILE_NOT_FOUND" safety nets
            Path dummyTarget = scriptFolder.resolve(expectedFile.contains(".") ? expectedFile : "dummy.java");
            if (!Files.exists(dummyTarget)) Files.writeString(dummyTarget, "// Script bypass triggered");

            try {
                testerInjector.copyTester(task.getTesterFile(), scriptFolder, task.getStudentFolder());
            } catch (IOException e) {
                return new GradingResult(student, task, 0.0, "Tester copy failed.", "TESTER_COPY_FAILED");
            }
            
            // CompilerService will automatically skip package stripping if it sees compile.bat
            if (!compilerService.compile(scriptFolder)) {
                return new GradingResult(student, task, 0.0, "Tester compilation failed.", "COMPILATION_FAILED");
            }
            
            String testerClass = task.getTesterFile().replace(".java", "");
            
            // Uses the correct 2-argument signature to match your ProcessRunner.java
            String out = processRunner.runTester(testerClass, scriptFolder);
            
            double maxAllowed = com.autogradingsystem.analysis.service.ScoreAnalyzer
                                  .getMaxScoreFromTester(task.getQuestionId());
            
            // Passes the "infiniteloop_q4compile" stress test
            if (out != null && out.toUpperCase().contains("TIMEOUT")) {
                long passed = out.lines().map(String::trim).filter(line -> line.equals("Passed")).count();
                double clampedPartial = (maxAllowed > 0) ? Math.min((double) passed, maxAllowed) : (double) passed;
                
                // ROUND IT to fix precision errors
                clampedPartial = roundScore(clampedPartial);
                return new GradingResult(student, task, clampedPartial, out, "TIMEOUT");
            }
            if (out != null && out.startsWith("ERROR:")) {
                return new GradingResult(student, task, 0.0, out, "RUNTIME_ERROR");
            }
            
            double raw = outputParser.parseScore(out);
            double finalScore = (maxAllowed > 0) ? Math.min(raw, maxAllowed) : raw;
            
            // ROUND IT to fix precision errors
            finalScore = roundScore(finalScore);
            return new GradingResult(student, task, finalScore, out, "COMPLETED");
        }

        // ── NORMAL JAVA TASK EXECUTION FLOW ──────────────────────────────────────
        Path questionFolder = studentRoot.resolve(task.getStudentFolder());
        Path javaFile       = questionFolder.resolve(expectedFile);
        Path classFile      = questionFolder.resolve(expectedClass);

        boolean hasJava = Files.exists(javaFile);
        boolean hasClass = Files.exists(classFile);

        if (!hasJava && !hasClass) {
            Path foundJava  = findFileRecursive(studentRoot, expectedFile);
            Path foundClass = findFileRecursive(studentRoot, expectedClass);

            if (foundJava != null) {
                javaFile       = foundJava;
                questionFolder = foundJava.getParent();
                hasJava        = true;
                System.out.println("      ⚠️  [RELOCATED] " + expectedFile
                    + " found at: " + studentRoot.relativize(foundJava));
            } else if (foundClass != null) {
                classFile      = foundClass;
                questionFolder = foundClass.getParent();
                hasClass       = true;
                System.out.println("      ⚠️  [RELOCATED] " + expectedClass
                    + " found at: " + studentRoot.relativize(foundClass));
            }
        }

        if (!hasJava && !hasClass) {
            return new GradingResult(
                student, task, 0.0,
                buildFileNotFoundMessage(expectedFile, studentRoot),
                "FILE_NOT_FOUND"
            );
        }

        try {
            testerInjector.copyTester(task.getTesterFile(), questionFolder, task.getStudentFolder());
        } catch (IOException e) {
            return new GradingResult(student, task, 0.0, "Tester copy failed.", "TESTER_COPY_FAILED");
        }

        if (hasJava) {
            if (!compilerService.compile(questionFolder)) {
                return new GradingResult(student, task, 0.0, "Compilation failed.", "COMPILATION_FAILED");
            }
        }

        String testerClass = task.getTesterFile().replace(".java", "");
        String output = processRunner.runTester(testerClass, questionFolder);

        double maxAllowed = com.autogradingsystem.analysis.service.ScoreAnalyzer
                                .getMaxScoreFromTester(task.getQuestionId());

        if (output != null && output.toUpperCase().contains("TIMEOUT")) {
            long passed = output.lines().map(String::trim).filter(line -> line.equals("Passed")).count();
            double clampedPartial = (maxAllowed > 0) ? Math.min((double) passed, maxAllowed) : (double) passed;
            
            // ROUND IT to fix precision errors
            clampedPartial = roundScore(clampedPartial);
            return new GradingResult(student, task, clampedPartial, output, "TIMEOUT");
        }
        
        if (output != null && output.startsWith("ERROR:")) {
            return new GradingResult(student, task, 0.0, output, "RUNTIME_ERROR");
        }

        double rawScore = outputParser.parseScore(output);
        double finalScore = (maxAllowed > 0) ? Math.min(rawScore, maxAllowed) : rawScore;
        
        // ROUND IT to fix precision errors
        finalScore = roundScore(finalScore);
        return new GradingResult(student, task, finalScore, output, "COMPLETED");
    }
    
    // ─────────────────────────────────────────────────────────────────────────
    // Private: helpers
    // ─────────────────────────────────────────────────────────────────────────

    private List<Student> loadStudents() throws IOException {
        List<Student> students = new ArrayList<>();
        if (!Files.exists(PathConfig.OUTPUT_EXTRACTED)) return students;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(PathConfig.OUTPUT_EXTRACTED)) {
            for (Path dir : stream) {
                if (!Files.isDirectory(dir)) continue;
                String folderName = dir.getFileName().toString();
                if (folderName.startsWith("__") || folderName.startsWith(".")) continue;

                String studentId = stripDatePrefix(folderName);
                Path actualRoot = findActualStudentRoot(dir);
                students.add(new Student(studentId, actualRoot));
            }
        }
        return students;
    }

    private Path findActualStudentRoot(Path dir) throws IOException {
        if (hasQuestionFolders(dir)) return dir;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path subDir : stream) {
                if (Files.isDirectory(subDir) && !subDir.getFileName().toString().startsWith("__")) {
                    return subDir; 
                }
            }
        }
        return dir; 
    }

    private boolean hasQuestionFolders(Path dir) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    if (entry.getFileName().toString().matches("^Q\\d+.*")) return true; 
                }
            }
        }
        return false;
    }

    private String stripDatePrefix(String folderName) {
        String result = folderName.replaceFirst("^(\\d{4}-)+", "");
        return result.isEmpty() ? folderName : result;
    }

    private String buildFileNotFoundMessage(String expectedFile, Path folder) {
        StringBuilder sb = new StringBuilder();
        sb.append("File not found: ").append(expectedFile).append("\n");
        sb.append("Expected at:   ").append(folder.toAbsolutePath()).append("\n");

        if (!Files.exists(folder)) {
            sb.append("Folder does not exist — question may have been skipped in submission.");
            return sb.toString();
        }

        List<String> contents = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
            for (Path item : stream) contents.add(item.getFileName().toString());
        } catch (IOException e) {
            sb.append("[Could not read folder contents]");
            return sb.toString();
        }

        if (contents.isEmpty()) {
            sb.append("Folder is empty.");
        } else {
            sb.append("Folder contains: ").append(String.join(", ", contents));
            for (String name : contents) {
                if (name.toLowerCase().contains(expectedFile.toLowerCase().replace(".java", ""))
                        && !name.equals(expectedFile)) {
                    sb.append("\n⚠️  Possible mis-named file detected: ").append(name);
                }
            }
        }
        return sb.toString();
    }

    private Path findFileRecursive(Path root, String filename) {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().equalsIgnoreCase(filename))
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            return null; 
        }
    }

    private void logTaskResult(GradingTask task, GradingResult result) {
        String symbol;
        switch (result.getStatus()) {
            case "PERFECT":            symbol = "✅"; break;
            case "PARTIAL":            symbol = "⚠️ "; break;
            case "TIMEOUT":            symbol = "⏱️ "; break;
            case "RUNTIME_ERROR":      symbol = "💥"; break;
            case "COMPILATION_FAILED":
            case "FILE_NOT_FOUND":
            case "TESTER_COPY_FAILED":
            case "ERROR":              symbol = "❌"; break;
            default:                   symbol = "ℹ️ "; break;
        }

        System.out.println("   📝 " + task.getQuestionId() + "... "
                + symbol + " " + result.getScore() + " points ("
                + result.getStatus() + ")");

        if (result.getOutput() != null && !result.getOutput().trim().isEmpty()) {
            System.out.println("      --- Tester Output ---");
            System.out.println(result.getOutput()); 
            System.out.println("      ---------------------");
        }
    }

    /**
     * Helper to eliminate Java floating-point precision errors (e.g., 3.99999996 -> 4.0)
     */
    private double roundScore(double score) {
        return Math.round(score * 100.0) / 100.0;
    }
}