package com.autogradingsystem.execution.controller;
import com.autogradingsystem.execution.service.TesterInjector;
import com.autogradingsystem.extraction.service.HeaderScanner;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public class ExecutionController {

    private final TesterInjector  testerInjector;
    private final CompilerService compilerService;
    private final ProcessRunner   processRunner;
    private final OutputParser    outputParser;

    private final Map<String, List<String>> remarksAccumulator = new LinkedHashMap<>();
    private List<Student> lastGradedStudents = new ArrayList<>();

    // ================================================================
    // Assessment-scoped paths for this grading run
    // ================================================================

    private final Path outputExtracted;
    private final Path csvScoresheet;
    private final Path inputTesters;
    private final Path inputTemplate;

    // ================================================================
    // CONSTRUCTORS
    // ================================================================

    /**
     * Path-aware constructor for multi-assessment support.
     * Called by AssessmentOrchestrator with per-assessment isolated paths.
     *
     * @param outputExtracted Path to the extracted students directory for this assessment
     * @param csvScoresheet   Path to the scoresheet CSV for this assessment
     * @param inputTesters    Path to this assessment's testers directory
     * @param inputTemplate   Path to this assessment's template directory
     */
    public ExecutionController(Path outputExtracted, Path csvScoresheet,
                               Path inputTesters, Path inputTemplate) {
        this.testerInjector  = new TesterInjector(inputTesters, inputTemplate);
        this.compilerService = new CompilerService();
        this.processRunner   = new ProcessRunner();
        this.outputParser    = new OutputParser();
        this.outputExtracted = outputExtracted;
        this.csvScoresheet   = csvScoresheet;
        this.inputTesters    = inputTesters;
        this.inputTemplate   = inputTemplate;
    }

    // ================================================================
    // PATH RESOLUTION
    // ================================================================

    private Path resolveOutputExtracted() { return outputExtracted; }
    private Path resolveCsvScoresheet()   { return csvScoresheet;   }
    private Path resolveInputTesters()    { return inputTesters;    }

    // ── Public: grade all students ────────────────────────────────────────────

    public List<GradingResult> gradeAllStudents(GradingPlan plan) throws IOException {
        return gradeAllStudents(plan, null);
    }

    public List<GradingResult> gradeAllStudents(GradingPlan plan,
                                                BiConsumer<Integer, Integer> progressCallback) throws IOException {
        List<Student> students = loadStudents(plan.getTasks());
        if (students.isEmpty()) {
            throw new IOException("No students found in: " + resolveOutputExtracted());
        }

        remarksAccumulator.clear();
        lastGradedStudents = students;

        for (Student student : students) {
            List<String> preRemarks = new ArrayList<>();

            if (!student.isFolderRenamed()) {
                preRemarks.add("NoFolderRename");
            }

            if (student.isHeaderMismatch()) {
                preRemarks.add("HeaderMismatch: " + student.getHeaderClaimedUsername()
                    + " header found in " + student.getHeaderMismatchFile()
                    + " (ZIP belongs to " + student.getId() + ")");
            }

            for (String missingFile : student.getMissingHeaderFiles()) {
                preRemarks.add("NoHeader:" + missingFile);
            }

            if (!preRemarks.isEmpty()) {
                remarksAccumulator.computeIfAbsent(student.getId(), k -> new ArrayList<>())
                                  .addAll(preRemarks);
            }
        }

        List<GradingResult> allResults = new ArrayList<>();
        int idx = 0;
        int totalUnits = students.size() * plan.getTasks().size();
        int completedUnits = 0;

        for (Student student : students) {
            idx++;
            System.out.println("\n👤 [" + idx + "/" + students.size() + "] " + student.getId());

            for (GradingTask task : plan.getTasks()) {
                GradingResult result;
                try {
                    result = gradeTask(student, task);
                } catch (Exception e) {
                    result = new GradingResult(student, task, 0.0,
                        "Unexpected error: " + e.getMessage(), "ERROR");
                }
                allResults.add(result);
                completedUnits++;
                if (progressCallback != null) {
                    progressCallback.accept(completedUnits, totalUnits);
                }
                logTaskResult(task, result);

                String status   = result.getStatus();
                double score    = result.getScore();
                double maxScore = com.autogradingsystem.analysis.service.ScoreAnalyzer
                                    .getMaxScoreFromTester(task.getQuestionId(), resolveInputTesters());

                String remarkLabel = null;
                switch (status) {
                    case "FILE_NOT_FOUND":     remarkLabel = "FILE_NOT_FOUND"; break;
                    case "COMPILATION_FAILED": remarkLabel = "SyntaxError"; break;
                    case "TIMEOUT":            remarkLabel = (score > 0) ? "PARTIAL TIMEOUT" : "TIMEOUT"; break;
                    case "RUNTIME_ERROR":
                    case "ERROR":
                    case "TESTER_COPY_FAILED": remarkLabel = "FAILED"; break;
                    case "COMPLETED":
                        if      (score == 0)       remarkLabel = "FAILED";
                        else if (score < maxScore) remarkLabel = "PARTIAL";
                        break;
                }

                if (remarkLabel != null) {
                    remarksAccumulator
                        .computeIfAbsent(student.getId(), k -> new ArrayList<>())
                        .add(task.getQuestionId() + ":" + remarkLabel);
                }
            }
        }

        return allResults;
    }

    // ── Public: result accessors ──────────────────────────────────────────────

    public Map<String, String> getRemarksByStudent() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Student student : lastGradedStudents) {
            if (student.isAnomaly()) continue;
            List<String> remarks = remarksAccumulator.getOrDefault(student.getId(), new ArrayList<>());
            out.put(student.getId(), remarks.isEmpty() ? "All Passed" : String.join("; ", remarks));
        }
        return out;
    }

    public Map<String, String> getAnomalyRemarksByStudent() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Student student : lastGradedStudents) {
            if (!student.isAnomaly()) continue;
            List<String> remarks = remarksAccumulator.getOrDefault(student.getId(), new ArrayList<>());
            out.put(student.getId(), String.join("; ", remarks));
        }
        return out;
    }

    public List<Student> getLastGradedStudents() {
        return Collections.unmodifiableList(lastGradedStudents);
    }

    // ── Private: grade one task ───────────────────────────────────────────────

    private GradingResult gradeTask(Student student, GradingTask task) throws IOException {

        Path   studentRoot   = student.getRootPath();
        String expectedFile  = task.getStudentFile();
        String expectedClass = expectedFile.replace(".java", ".class");

        // ── DYNAMIC SCRIPT ROUTER ────────────────────────────────────────────
        boolean isScriptTask = false;
        Path    scriptFolder = null;

        if (expectedFile.toLowerCase().endsWith(".bat") || expectedFile.toLowerCase().endsWith(".sh")) {
            String baseName = expectedFile.substring(0, expectedFile.lastIndexOf('.'));
            Path found = findFileRecursive(studentRoot, baseName + ".bat");
            if (found == null) found = findFileRecursive(studentRoot, baseName + ".sh");
            if (found != null) { isScriptTask = true; scriptFolder = found.getParent(); }

        } else if (!expectedFile.contains(".")) {
            Path found = findFileRecursive(studentRoot, "compile.bat");
            if (found == null) found = findFileRecursive(studentRoot, "run.bat");
            if (found == null) found = findFileRecursive(studentRoot, "compile.sh");
            if (found == null) found = findFileRecursive(studentRoot, "run.sh");

            if (found == null && studentRoot.getParent() != null) {
                Path parentDir   = studentRoot.getParent();
                Path grandParent = parentDir.getParent();
                boolean parentIsRoot = grandParent == null ||
                    parentDir.toAbsolutePath().equals(resolveOutputExtracted().toAbsolutePath());

                if (!parentIsRoot) {
                    try (DirectoryStream<Path> siblings = Files.newDirectoryStream(parentDir)) {
                        for (Path sibling : siblings) {
                            if (!Files.isDirectory(sibling) || sibling.equals(studentRoot)) continue;
                            Path siblingQ4 = sibling.resolve("Q4");
                            if (!Files.isDirectory(siblingQ4)) continue;
                            found = findFileRecursive(siblingQ4, "compile.sh");
                            if (found == null) found = findFileRecursive(siblingQ4, "compile.bat");
                            if (found == null) found = findFileRecursive(siblingQ4, "run.sh");
                            if (found == null) found = findFileRecursive(siblingQ4, "run.bat");
                            if (found != null) {
                                System.out.println("      ⚠️  [Q4 RELOCATED] Found scripts in sibling dir: " + found.getParent());
                                break;
                            }
                        }
                    } catch (IOException ignored) {}
                }
            }
            if (found != null) { isScriptTask = true; scriptFolder = found.getParent(); }
        }

        if (isScriptTask) {
            if (scriptFolder == null || !Files.exists(scriptFolder))
                return new GradingResult(student, task, 0.0, "Required script not found.", "FILE_NOT_FOUND");

            Path dummyTarget = scriptFolder.resolve(expectedFile.contains(".") ? expectedFile : "dummy.java");
            if (!Files.exists(dummyTarget)) Files.writeString(dummyTarget, "// Script bypass triggered");

            try {
                testerInjector.copyTester(task.getTesterFile(), scriptFolder, task.getStudentFolder());
            } catch (IOException e) {
                return new GradingResult(student, task, 0.0, "Tester copy failed.", "TESTER_COPY_FAILED");
            }

            if (!compilerService.compile(scriptFolder))
                return new GradingResult(student, task, 0.0, "Tester compilation failed.", "COMPILATION_FAILED");

            String testerClass = task.getTesterFile().replace(".java", "");
            String out = processRunner.runTester(testerClass, scriptFolder, scriptFolder.toAbsolutePath().toString());
            double maxAllowed = com.autogradingsystem.analysis.service.ScoreAnalyzer.getMaxScoreFromTester(task.getQuestionId(), resolveInputTesters());

            if (out != null && out.toUpperCase().contains("TIMEOUT")) {
                long passed = out.lines().map(String::trim).filter(l -> l.equals("Passed")).count();
                return new GradingResult(student, task, roundScore(Math.min((double) passed, maxAllowed)), out, "TIMEOUT");
            }
            if (out != null && out.startsWith("ERROR:"))
                return new GradingResult(student, task, 0.0, out, "RUNTIME_ERROR");

            double raw = outputParser.parseScore(out);
            return new GradingResult(student, task, roundScore(maxAllowed > 0 ? Math.min(raw, maxAllowed) : raw), out, "COMPLETED");
        }

        // ── NORMAL JAVA TASK ─────────────────────────────────────────────────
        Path questionFolder = studentRoot.resolve(task.getStudentFolder());
        Path javaFile       = questionFolder.resolve(expectedFile);
        Path classFile      = questionFolder.resolve(expectedClass);

        boolean hasJava  = Files.exists(javaFile);
        boolean hasClass = Files.exists(classFile);

        if (!hasJava && !hasClass) {
            Path foundQFolder = findFolderRecursive(studentRoot, task.getStudentFolder());
            System.out.println("DEBUG FIND [" + student.getId() + "] looking for folder=" 
    + task.getStudentFolder() + " in " + studentRoot);
            if (foundQFolder != null) {
                Path foundJava  = findFileRecursive(foundQFolder, expectedFile);
                Path foundClass = findFileRecursive(foundQFolder, expectedClass);
                if (foundJava != null) {
                    javaFile = foundJava; questionFolder = foundJava.getParent(); hasJava = true;
                } else if (foundClass != null) {
                    classFile = foundClass; questionFolder = foundClass.getParent(); hasClass = true;
                }
            }
        }

        if (!hasJava && !hasClass)
            return new GradingResult(student, task, 0.0,
                buildFileNotFoundMessage(expectedFile, studentRoot), "FILE_NOT_FOUND");

        if (!hasJava && hasClass)
            return new GradingResult(student, task, 0.0,
                "Source file not found: " + expectedFile
                + "\nOnly a pre-compiled .class was submitted — source (.java) is required.",
                "FILE_NOT_FOUND");

        try {
            testerInjector.copyTester(task.getTesterFile(), questionFolder, task.getStudentFolder());
        } catch (IOException e) {
            return new GradingResult(student, task, 0.0, "Tester copy failed.", "TESTER_COPY_FAILED");
        }

        if (hasJava) {
            deletePrecompiledClasses(questionFolder, expectedFile);

            CompilerService.CompileResult compileResult =
                compilerService.compileTargetedWithDetails(questionFolder, expectedFile);

            if (!compileResult.success)
                return new GradingResult(student, task, 0.0, "Compilation failed.", "COMPILATION_FAILED");

            for (String strippedFile : compileResult.strippedPackageFiles) {
                remarksAccumulator
                    .computeIfAbsent(student.getId(), k -> new ArrayList<>())
                    .add(task.getQuestionId() + ":WrongPackage:" + strippedFile);
            }
        }

        String testerClass = task.getTesterFile().replace(".java", "");
        String output = processRunner.runTester(testerClass, questionFolder);
        double maxAllowed = com.autogradingsystem.analysis.service.ScoreAnalyzer.getMaxScoreFromTester(task.getQuestionId(), resolveInputTesters());

        if (output != null && output.toUpperCase().contains("TIMEOUT")) {
            long passed = output.lines().map(String::trim).filter(l -> l.equals("Passed")).count();
            return new GradingResult(student, task,
                roundScore(maxAllowed > 0 ? Math.min((double) passed, maxAllowed) : (double) passed),
                output, "TIMEOUT");
        }
        if (output != null && output.startsWith("ERROR:"))
            return new GradingResult(student, task, 0.0, output, "RUNTIME_ERROR");

        double rawScore = outputParser.parseScore(output);
        return new GradingResult(student, task,
            roundScore(maxAllowed > 0 ? Math.min(rawScore, maxAllowed) : rawScore),
            output, "COMPLETED");
    }

    // ── Private: identity resolution ─────────────────────────────────────────

    private List<Student> loadStudents(List<GradingTask> tasks) throws IOException {
        List<Student> students = new ArrayList<>();
        Path extracted = resolveOutputExtracted();
        if (!Files.exists(extracted)) return students;

        Map<String, String> emailToUsername = loadEmailToUsernameMap();
        HeaderScanner scanner = new HeaderScanner();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(extracted)) {
            for (Path dir : stream) {
                if (!Files.isDirectory(dir)) continue;
                String folderName = dir.getFileName().toString();
                if (folderName.startsWith("__") || folderName.startsWith(".")) continue;

                String strippedName = stripDatePrefix(folderName);
                Path   actualRoot   = findActualStudentRoot(dir);

                HeaderScanner.ScanResult headerScan = scanner.scan(actualRoot, tasks);

                boolean folderRenamed    = isValidUsername(strippedName, emailToUsername);
                String  resolvedUsername;

                if (folderRenamed) {
                    resolvedUsername = strippedName;
                } else if (headerScan.resolvedEmail != null &&
                           emailToUsername.containsKey(headerScan.resolvedEmail)) {
                    resolvedUsername = emailToUsername.get(headerScan.resolvedEmail);
                    folderRenamed = false;
                } else {
                    resolvedUsername = strippedName;
                    folderRenamed = false;
                }

                Student student = new Student(resolvedUsername, actualRoot);
                student.setFolderRenamed(folderRenamed);
                student.setMissingHeaderFiles(headerScan.missingHeaders);
                student.setRawFolderName(folderName);

                boolean isAnomaly = !folderRenamed &&
                    (headerScan.resolvedEmail == null ||
                     !emailToUsername.containsKey(headerScan.resolvedEmail));
                student.setAnomaly(isAnomaly);

                if (folderRenamed && headerScan.resolvedEmail != null) {
                    String headerUsername = emailToUsername.get(headerScan.resolvedEmail);
                    if (headerUsername != null && !headerUsername.equals(resolvedUsername)) {
                        student.setHeaderMismatch(true);
                        student.setHeaderClaimedUsername(headerUsername);
                        student.setHeaderMismatchFile(headerScan.resolvedFromFile);
                    }
                }

                students.add(student);
            }
        }
        return students;
    }

    private boolean isValidUsername(String name, Map<String, String> emailToUsername) {
        return emailToUsername.containsValue(name);
    }

    private Map<String, String> loadEmailToUsernameMap() {
        Map<String, String> map = new LinkedHashMap<>();
        try (var reader = Files.newBufferedReader(resolveCsvScoresheet())) {
            reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] cols = line.split(",", -1);
                if (cols.length > 4) {
                    String username = cols[1].replace("#", "").trim();
                    String email    = cols[4].trim().toLowerCase();
                    map.put(email, username);
                }
            }
        } catch (IOException e) {
            System.out.println("⚠️  Could not load scoresheet for email mapping: " + e.getMessage());
        }
        return map;
    }

    // ── Private: path helpers ─────────────────────────────────────────────────

    private Path findActualStudentRoot(Path dir) throws IOException {
        if (hasQuestionFolders(dir)) return dir;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path subDir : stream) {
                if (Files.isDirectory(subDir) && !subDir.getFileName().toString().startsWith("__"))
                    return subDir;
            }
        }
        return dir;
    }

    private boolean hasQuestionFolders(Path dir) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream)
                if (Files.isDirectory(entry) && entry.getFileName().toString().matches("(?i)^Q\\d+.*"))
                    return true;
        }
        return false;
    }

    private String stripDatePrefix(String folderName) {
        String result = folderName.replaceFirst("^(\\d{4}-)+", "");
        return result.isEmpty() ? folderName : result;
    }

    private Path findFolderRecursive(Path root, String folderName) {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk
                .filter(Files::isDirectory)
                .filter(p -> p.getFileName().toString().equalsIgnoreCase(folderName))
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            return null;
        }
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

    private String buildFileNotFoundMessage(String expectedFile, Path folder) {
        StringBuilder sb = new StringBuilder();
        sb.append("File not found: ").append(expectedFile).append("\n");
        sb.append("Expected at:   ").append(folder.toAbsolutePath()).append("\n");

        if (!Files.exists(folder)) {
            sb.append("Folder does not exist — question may have been skipped.");
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
                        && !name.equals(expectedFile))
                    sb.append("\n⚠️  Possible mis-named file detected: ").append(name);
            }
        }
        return sb.toString();
    }

    // ── Private: compilation helpers ─────────────────────────────────────────

    private void deletePrecompiledClasses(Path folder, String javaFilename) {
        String stem = javaFilename.replace(".java", "");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, "*.class")) {
            for (Path classFile : stream) {
                String name = classFile.getFileName().toString();
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
            System.out.println("      ⚠️  [SECURITY] Could not scan for pre-compiled classes: " + e.getMessage());
        }
    }

    private void logTaskResult(GradingTask task, GradingResult result) {
        String symbol;
        switch (result.getStatus()) {
            case "TIMEOUT":            symbol = "⏱️ "; break;
            case "RUNTIME_ERROR":      symbol = "💥"; break;
            case "COMPILATION_FAILED":
            case "FILE_NOT_FOUND":
            case "TESTER_COPY_FAILED":
            case "ERROR":              symbol = "❌"; break;
            default:                   symbol = result.getScore() > 0 ? "✅" : "❌"; break;
        }
        System.out.println("   📝 " + task.getQuestionId() + "... "
            + symbol + " " + result.getScore() + " points (" + result.getStatus() + ")");

        if (result.getOutput() != null && !result.getOutput().trim().isEmpty()) {
            System.out.println("      --- Tester Output ---");
            System.out.println(result.getOutput());
            System.out.println("      ---------------------");
        }
    }

    private double roundScore(double score) {
        return Math.round(score * 100.0) / 100.0;
    }
}
