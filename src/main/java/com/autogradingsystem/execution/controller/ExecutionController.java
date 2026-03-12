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
import java.util.stream.Stream; // <-- ADDED MISSING IMPORT

/**
 * ExecutionController - Orchestrates Phase 3 (Grading Execution)
 * PURPOSE:
 * - Coordinates grading execution workflow
 * - Acts as entry point for execution service
 * - Called by Main.java during grading phase
 * * RESPONSIBILITIES:
 * - Load all students from extracted directory
 * - Execute grading for each student and task
 * - Compile student code + testers
 * - Run testers and capture output
 * - Parse scores and create results
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

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────────────────
    // Private: grading logic
    // ─────────────────────────────────────────────────────────────────────────

    private GradingResult gradeTask(Student student, GradingTask task) throws IOException {

        // Strategy: locate the student's file using a two-step approach.
        //   Step 1 - Exact path lookup (fast, covers correct submissions):
        //            student/<Q1>/<Q1a.java>
        //   Step 2 - Recursive case-insensitive search across entire student folder.
        //            Handles: wrong Q folder, nested subfolders, wrong casing.
        //            e.g. Q2/Q1a.java, Q1/src/Q1a.java, q1a.java

        Path   studentRoot   = student.getRootPath();
        String expectedFile  = task.getStudentFile();                   // e.g. "Q1a.java"
        String expectedClass = expectedFile.replace(".java", ".class"); // e.g. "Q1a.class"

        // Step 1 - exact path
        Path questionFolder = studentRoot.resolve(task.getStudentFolder());
        Path javaFile       = questionFolder.resolve(expectedFile);
        Path classFile      = questionFolder.resolve(expectedClass);

        boolean hasJava = Files.exists(javaFile);
        boolean hasClass = Files.exists(classFile);

        // Step 2 - recursive fallback if exact path failed
        if (!hasJava && !hasClass) {
            Path foundJava  = findFileRecursive(studentRoot, expectedFile);
            Path foundClass = findFileRecursive(studentRoot, expectedClass);

            if (foundJava != null) {
                javaFile       = foundJava;
                questionFolder = foundJava.getParent();
                hasJava        = true;
                System.out.println("      ⚠️  [RELOCATED] " + expectedFile
                    + " found at: " + studentRoot.relativize(foundJava)
                    + " (expected: " + task.getStudentFolder() + "/" + expectedFile + ")");
            } else if (foundClass != null) {
                classFile      = foundClass;
                questionFolder = foundClass.getParent();
                hasClass       = true;
                System.out.println("      ⚠️  [RELOCATED] " + expectedClass
                    + " found at: " + studentRoot.relativize(foundClass)
                    + " (expected: " + task.getStudentFolder() + "/" + expectedClass + ")");
            }
        }

        // FILE NOT FOUND - file not found anywhere in student folder
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

        // ── RUN TESTER ──────────────────────────────────────────────────────
        String testerClass = task.getTesterFile().replace(".java", "");

        // Q4Tester is special: it runs the student's own compile.sh / run.sh
        // and needs to know WHERE the student's Q4 folder is (passed as args[0]).
        // For all other questions, no extra arg is needed.
        String q4Arg = null;
        if (task.getQuestionId().equalsIgnoreCase("Q4")) {
            q4Arg = questionFolder.toAbsolutePath().toString();
        }
        String output = processRunner.runTester(testerClass, questionFolder, q4Arg);

        // ── DYNAMIC MAX SCORE ────────────────────────────────────────────────
        // Sums actual values after "score +=" in the tester file, so Q4's
        // "score += 6.0" correctly yields maxAllowed=6.0, not 1.0.
        double maxAllowed = com.autogradingsystem.analysis.service.ScoreAnalyzer
                                .getMaxScoreFromTester(task.getQuestionId());

        // ── DETECT RUNTIME FAILURES ─────────────────────────────────────────
        if (output != null && output.toUpperCase().contains("TIMEOUT")) {
            long passed = output.lines()
                .map(String::trim)
                .filter(line -> line.equals("Passed"))
                .count();
            double clampedPartial = Math.min((double) passed, maxAllowed);
            return new GradingResult(student, task, clampedPartial, output, "TIMEOUT");
        }
        if (output != null && output.toUpperCase().contains("ERROR")) {
            return new GradingResult(student, task, 0.0, output, "RUNTIME_ERROR");
        }

        // ── PARSE SCORE & APPLY DYNAMIC CLAMP ───────────────────────────────
        double rawScore = outputParser.parseScore(output);

        return new GradingResult(student, task, Math.min(rawScore, maxAllowed), output, "COMPLETED");
    }
    
    // ─────────────────────────────────────────────────────────────────────────
    // Private: helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Scans OUTPUT_EXTRACTED and returns one Student per subdirectory.
     */
    private List<Student> loadStudents() throws IOException {
        List<Student> students = new ArrayList<>();

        if (!Files.exists(PathConfig.OUTPUT_EXTRACTED)) return students;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(PathConfig.OUTPUT_EXTRACTED)) {
            for (Path dir : stream) {
                if (!Files.isDirectory(dir)) continue;

                String folderName = dir.getFileName().toString();

                // Skip __MACOSX - This is crucial for Windows machines!
                if (folderName.startsWith("__") || folderName.startsWith(".")) continue;

                // Strip leading date prefixes
                String studentId = stripDatePrefix(folderName);

                // --- FIX: Handle Nested Folders ---
                Path actualRoot = findActualStudentRoot(dir);

                students.add(new Student(studentId, actualRoot));
            }
        }
        return students;
    }

    /** Helper: Searches for the actual folder containing the Q1, Q2 directories. */
    private Path findActualStudentRoot(Path dir) throws IOException {
        // 1. If the current directory already has Q folders, we are good.
        if (hasQuestionFolders(dir)) {
            return dir;
        }

        // 2. If not, look exactly one level deeper for the true root (e.g., chee.teo.2022)
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path subDir : stream) {
                if (Files.isDirectory(subDir) && !subDir.getFileName().toString().startsWith("__")) {
                    return subDir; // Use this nested folder as the real root
                }
            }
        }
        return dir; // Fallback
    }

    /** * Helper: Dynamically checks if a directory contains ANY question folders 
     * by looking for folders that start with "Q" followed by a number (e.g., Q1, Q4, Q10).
     */
    private boolean hasQuestionFolders(Path dir) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    String folderName = entry.getFileName().toString();
                    
                    // Regex: ^Q\\d+.* means "Starts with Q, followed by at least 1 digit, then anything"
                    if (folderName.matches("^Q\\d+.*")) {
                        return true; // Found a question folder!
                    }
                }
            }
        }
        return false;
    }

    /**
     * Strips leading YYYY- or YYYY-YYYY- prefixes from folder names.
     */
    private String stripDatePrefix(String folderName) {
        String result = folderName.replaceFirst("^(\\d{4}-)+", "");
        return result.isEmpty() ? folderName : result;
    }

    /** Builds a helpful error message when the student's file isn't where expected. */
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
                    sb.append("\n⚠️  Possible mis-named file detected: ").append(name)
                      .append(" (expected: ").append(expectedFile).append(")");
                }
            }
        }
        return sb.toString();
    }

    // ── RESTORED MISSING METHOD ─────────────────────────────────────────────
    /**
     * Recursively searches for a file by name (case-insensitive) within a directory tree.
     * Used as a fallback when the exact expected path doesn't exist.
     */
    private Path findFileRecursive(Path root, String filename) {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().equalsIgnoreCase(filename))
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            return null; // Non-fatal: if walk fails, treat as not found
        }
    }
    // ─────────────────────────────────────────────────────────────────────────

    /** Prints a concise one-line result per task, plus the raw tester output. */
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

        // Print the summary line
        System.out.println("   📝 " + task.getQuestionId() + "... "
                + symbol + " " + result.getScore() + " points ("
                + result.getStatus() + ")");

        // --- NEW CODE TO SHOW EXPECTED/ACTUAL OUTPUT ---
        if (result.getOutput() != null && !result.getOutput().trim().isEmpty()) {
            System.out.println("      --- Tester Output ---");
            System.out.println(result.getOutput()); 
            System.out.println("      ---------------------");
        }
    }
}