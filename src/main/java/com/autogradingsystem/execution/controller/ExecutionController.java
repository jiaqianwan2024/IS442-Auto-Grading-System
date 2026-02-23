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
 * ExecutionController - Orchestrates Phase 3 (Grading Execution)
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

        // Use the student's actual rootPath directly, not PathConfig reconstruction
        Path questionFolder = student.getRootPath().resolve(task.getStudentFolder());
        Path javaFile       = questionFolder.resolve(task.getStudentFile());
        String classFileName = task.getStudentFile().replace(".java", ".class");
        Path classFile       = questionFolder.resolve(classFileName);

        boolean hasJava  = Files.exists(javaFile);
        boolean hasClass = Files.exists(classFile);

        // ── FILE NOT FOUND ──────────────────────────────────────────────────
        if (!hasJava && !hasClass) {
            return new GradingResult(
                student, task, 0.0,
                buildFileNotFoundMessage(task.getStudentFile(), questionFolder),
                "FILE_NOT_FOUND"
            );
        }

        // ── INJECT TESTER + DATA FILES ──────────────────────────────────────
        try {
            testerInjector.copyTester(task.getTesterFile(), questionFolder, task.getStudentFolder());
        } catch (IOException e) {
            return new GradingResult(
                student, task, 0.0,
                "Tester copy failed: " + e.getMessage(),
                "TESTER_COPY_FAILED"
            );
        }

        // ── COMPILE (skip if .class-only submission) ────────────────────────
        if (hasJava) {
            boolean compiled = compilerService.compile(questionFolder);
            if (!compiled) {
                return new GradingResult(
                    student, task, 0.0,
                    "Compilation failed — check student code for syntax errors or package declarations.",
                    "COMPILATION_FAILED"
                );
            }
        }

        // ── RUN TESTER ──────────────────────────────────────────────────────
        String testerClass = task.getTesterFile().replace(".java", "");
        String output = processRunner.runTester(testerClass, questionFolder);

        // ── DETECT RUNTIME FAILURES ─────────────────────────────────────────
        if (output != null && output.toUpperCase().startsWith("TIMEOUT")) {
            return new GradingResult(student, task, 0.0, output, "TIMEOUT");
        }
        if (output != null && output.toUpperCase().startsWith("ERROR")) {
            return new GradingResult(student, task, 0.0, output, "RUNTIME_ERROR");
        }

        // ── PARSE SCORE ─────────────────────────────────────────────────────
        double score = outputParser.parseScore(output);
        return new GradingResult(student, task, score, output);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private: helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Scans OUTPUT_EXTRACTED and returns one Student per subdirectory.
     *
     * EDGE CASES:
     * - Skips __MACOSX folders created by macOS ZIP tools
     * - Strips date prefixes like "2023-2024-" from folder names so the
     *   student ID matches the username in the score sheet
     * - Uses the actual folder Path as rootPath so gradeTask() never
     *   needs to re-resolve through PathConfig
     */
    private List<Student> loadStudents() throws IOException {
        List<Student> students = new ArrayList<>();

        if (!Files.exists(PathConfig.OUTPUT_EXTRACTED)) return students;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(PathConfig.OUTPUT_EXTRACTED)) {
            for (Path dir : stream) {
                if (!Files.isDirectory(dir)) continue;

                String folderName = dir.getFileName().toString();

                // Skip __MACOSX and other hidden/system folders
                if (folderName.startsWith("__") || folderName.startsWith(".")) continue;

                // Strip leading date prefixes e.g. "2023-2024-chee.teo.2022" → "chee.teo.2022"
                String studentId = stripDatePrefix(folderName);

                students.add(new Student(studentId, dir));
            }
        }
        return students;
    }

    /**
     * Strips leading YYYY- or YYYY-YYYY- prefixes from folder names.
     *
     * EXAMPLES:
     * "2023-2024-chee.teo.2022" → "chee.teo.2022"
     * "2024-david.2024"         → "david.2024"
     * "chee.teo.2022"           → "chee.teo.2022" (unchanged)
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

    /** Prints a concise one-line result per task. */
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
    }
}