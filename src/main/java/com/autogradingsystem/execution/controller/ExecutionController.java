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

        // ── DYNAMIC SCRIPT ROUTER ────────────────────────────────────────────────
        boolean isScriptTask = false;
        Path scriptFolder = null;

        // Route 1: ScoreSheet explicitly targets a script
        if (expectedFile.toLowerCase().endsWith(".bat") || expectedFile.toLowerCase().endsWith(".sh")) {
            String baseName = expectedFile.substring(0, expectedFile.lastIndexOf('.'));
            Path found = findFileRecursive(studentRoot, baseName + ".bat");
            if (found == null) found = findFileRecursive(studentRoot, baseName + ".sh");
            if (found != null) {
                isScriptTask = true;
                scriptFolder = found.getParent();
            }
        }
        // Route 2: Target is a Folder Task (e.g., "Q4" with no extension)
        else if (!expectedFile.contains(".")) {
            Path found = findFileRecursive(studentRoot, "compile.bat");
            if (found == null) found = findFileRecursive(studentRoot, "run.bat");
            if (found == null) found = findFileRecursive(studentRoot, "compile.sh");
            if (found == null) found = findFileRecursive(studentRoot, "run.sh");

            // Fallback: some zips have Q1-Q3 in one sub-folder and Q4 in a sibling sub-folder.
            // e.g. zipa_folderb: Q1-Q3 in other.student.2025/, Q4 in zipa_folderb.2025/Q4/
            // SAFETY RULE: only search siblings whose folder name shares the same student ID
            // (i.e. the extraction wrapper folder name). This prevents accidentally grading
            // another student's Q4 when the current student submitted no Q4 at all.
            if (found == null && studentRoot.getParent() != null) {
                // studentRoot is e.g. extracted/zipa_folderb.2025/other.student.2025/
                // its parent is e.g. extracted/zipa_folderb.2025/
                // grandparent is extracted/
                // We only search OTHER sub-folders of the SAME parent (same zip wrapper),
                // not the entire extraction dir which contains all students' folders.
                Path parentDir = studentRoot.getParent();
                Path grandParentDir = parentDir.getParent();

                // Guard: parentDir must be a named student wrapper (not the raw extraction root)
                // We detect this by checking that parentDir is NOT the extraction root itself.
                boolean parentIsExtractionRoot = grandParentDir == null ||
                    parentDir.toAbsolutePath().equals(
                        com.autogradingsystem.PathConfig.OUTPUT_EXTRACTED.toAbsolutePath());

                if (!parentIsExtractionRoot) {
                    try (java.nio.file.DirectoryStream<Path> siblings =
                             java.nio.file.Files.newDirectoryStream(parentDir)) {
                        for (Path sibling : siblings) {
                            if (!java.nio.file.Files.isDirectory(sibling)) continue;
                            if (sibling.equals(studentRoot)) continue; // skip self
                            // Only look inside a Q4 subfolder of the sibling, not freely
                            Path siblingQ4 = sibling.resolve("Q4");
                            if (!java.nio.file.Files.isDirectory(siblingQ4)) continue;
                            found = findFileRecursive(siblingQ4, "compile.sh");
                            if (found == null) found = findFileRecursive(siblingQ4, "compile.bat");
                            if (found == null) found = findFileRecursive(siblingQ4, "run.sh");
                            if (found == null) found = findFileRecursive(siblingQ4, "run.bat");
                            if (found != null) {
                                System.out.println("      ⚠️  [Q4 RELOCATED] Found scripts in sibling dir: " + found.getParent());
                                break;
                            }
                        }
                    } catch (java.io.IOException ignored) {}
                }
            }

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

            Path dummyTarget = scriptFolder.resolve(expectedFile.contains(".") ? expectedFile : "dummy.java");
            if (!Files.exists(dummyTarget)) Files.writeString(dummyTarget, "// Script bypass triggered");

            try {
                testerInjector.copyTester(task.getTesterFile(), scriptFolder, task.getStudentFolder());
            } catch (IOException e) {
                return new GradingResult(student, task, 0.0, "Tester copy failed.", "TESTER_COPY_FAILED");
            }

            if (!compilerService.compile(scriptFolder)) {
                return new GradingResult(student, task, 0.0, "Tester compilation failed.", "COMPILATION_FAILED");
            }

            String testerClass = task.getTesterFile().replace(".java", "");
            String out = processRunner.runTester(testerClass, scriptFolder, scriptFolder.toAbsolutePath().toString());

            double maxAllowed = com.autogradingsystem.analysis.service.ScoreAnalyzer
                                  .getMaxScoreFromTester(task.getQuestionId());

            if (out != null && out.toUpperCase().contains("TIMEOUT")) {
                long passed = out.lines().map(String::trim).filter(line -> line.equals("Passed")).count();
                double clampedPartial = (maxAllowed > 0) ? Math.min((double) passed, maxAllowed) : (double) passed;
                return new GradingResult(student, task, roundScore(clampedPartial), out, "TIMEOUT");
            }
            if (out != null && out.startsWith("ERROR:")) {
                return new GradingResult(student, task, 0.0, out, "RUNTIME_ERROR");
            }

            double raw = outputParser.parseScore(out);
            double finalScore = (maxAllowed > 0) ? Math.min(raw, maxAllowed) : raw;
            return new GradingResult(student, task, roundScore(finalScore), out, "COMPLETED");
        }

        // ── NORMAL JAVA TASK EXECUTION FLOW ─────────────────────────────────────
        Path questionFolder = studentRoot.resolve(task.getStudentFolder());
        Path javaFile       = questionFolder.resolve(expectedFile);
        Path classFile      = questionFolder.resolve(expectedClass);

        boolean hasJava  = Files.exists(javaFile);
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

        // SECURITY POLICY: .class-only submissions get 0.
        // If a student submitted only a pre-compiled .class with no source, we cannot verify
        // their work and the binary could be anything. Require .java source to be present.
        if (!hasJava && hasClass) {
            return new GradingResult(
                student, task, 0.0,
                "Source file not found: " + expectedFile + "\n"
                + "Only a pre-compiled .class was submitted — source (.java) is required for grading.",
                "FILE_NOT_FOUND"
            );
        }

        try {
            testerInjector.copyTester(task.getTesterFile(), questionFolder, task.getStudentFolder());
        } catch (IOException e) {
            return new GradingResult(student, task, 0.0, "Tester copy failed.", "TESTER_COPY_FAILED");
        }

        if (hasJava) {
            // SECURITY: Delete any pre-existing .class file for the target before compiling.
            // Without this, a student can submit an empty .java alongside a pre-compiled .class
            // (from a working solution). The empty .java compiles cleanly (javac exit 0),
            // the old .class survives, and the tester grades the pre-compiled binary — not the source.
            deletePrecompiledClasses(questionFolder, expectedFile);

            // Use targeted compile: only compile this specific file + tester.
            // Compiling the whole folder would cause broken siblings (e.g. Q1a syntax error)
            // to prevent valid files (Q1b) from being compiled and graded.
            if (!compilerService.compileTargeted(questionFolder, expectedFile)) {
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
            return new GradingResult(student, task, roundScore(clampedPartial), output, "TIMEOUT");
        }

        if (output != null && output.startsWith("ERROR:")) {
            return new GradingResult(student, task, 0.0, output, "RUNTIME_ERROR");
        }

        double rawScore = outputParser.parseScore(output);
        double finalScore = (maxAllowed > 0) ? Math.min(rawScore, maxAllowed) : rawScore;
        return new GradingResult(student, task, roundScore(finalScore), output, "COMPLETED");
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

    private double roundScore(double score) {
        return Math.round(score * 100.0) / 100.0;
    }

    /**
     * Deletes pre-existing .class files for the target student file (and any inner classes)
     * from the student's question folder BEFORE compilation.
     *
     * WHY: An empty (or stripped) .java compiles cleanly with exit 0 but produces no .class.
     * If a pre-compiled .class from a working solution is already sitting in the folder,
     * the tester will load it and award full marks — even though the student's source is empty.
     *
     * SAFE: We only delete .class files whose stem matches the student file being graded
     * (e.g. "Q2a.class", "Q2a$InnerClass.class"). We never delete dependency .class files
     * like Shape.class, DataException.class, etc.
     *
     * @param folder       the student's question folder
     * @param javaFilename e.g. "Q2a.java"
     */
    private void deletePrecompiledClasses(Path folder, String javaFilename) {
        String stem = javaFilename.replace(".java", "");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, "*.class")) {
            for (Path classFile : stream) {
                String name = classFile.getFileName().toString();
                // Match "Q2a.class" and inner classes like "Q2a$Helper.class"
                if (name.equals(stem + ".class") || name.startsWith(stem + "$")) {
                    try {
                        Files.delete(classFile);
                        System.out.println("      🗑️  [SECURITY] Deleted pre-existing: " + name);
                    } catch (IOException e) {
                        System.out.println("      ⚠️  [SECURITY] Could not delete " + name + ": " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            // Non-fatal — if we can't list the folder, compilation will still run
            System.out.println("      ⚠️  [SECURITY] Could not scan for pre-compiled classes: " + e.getMessage());
        }
    }
}