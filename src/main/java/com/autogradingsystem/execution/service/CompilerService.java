package com.autogradingsystem.execution.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * CompilerService - Compiles Java Files in a Student Folder
 * 
 * PURPOSE:
 * - Compiles all .java files in a directory
 * - Suppresses error output for clean logs (FIX 2)
 * - Provides concise compilation status
 * - Cross-platform support (Windows/Mac/Linux)
 * 
 * WORKFLOW:
 * 1. Build javac command for all *.java files in directory
 * 2. Execute javac process
 * 3. Wait for completion
 * 4. Check exit code (0 = success, non-zero = failure)
 * 5. Return boolean result
 * 
 * ERROR HANDLING (FIX 2):
 * - Compilation errors are captured but NOT displayed
 * - Shows concise summary: "❌ Compilation failed (X error(s))"
 * - Prevents error flood in console
 * - Keeps output clean and readable
 * 
 * CHANGES FROM v3.0:
 * - Removed saveErrorLog() method (dead code)
 * - Removed SAVE_ERROR_LOGS constant (unused)
 * - Error suppression already implemented
 * - Package updated
 */
public class CompilerService {

    /** Max time to wait for javac before killing it */
    private static final int COMPILER_TIMEOUT_SECONDS = 30;

    /**
     * Compiles all .java files in the given directory.
     *
     * WORKFLOW:
     * 1. Check .java files exist
     * 2. Strip package declarations (edge case: students submitting with packages)
     * 3. Run javac on all files
     * 4. Return success/failure
     *
     * @param workingDir Directory containing .java files
     * @return true if compilation succeeded
     */
    public boolean compile(Path workingDir) {

        // Guard: directory must exist
        if (workingDir == null || !Files.exists(workingDir)) {
            System.out.println("[Compiler] ❌ Directory does not exist: " + workingDir);
            return false;
        }

        // Collect .java files
        List<Path> javaFiles = collectJavaFiles(workingDir);
        if (javaFiles.isEmpty()) {
            System.out.println("[Compiler] ❌ No .java files found in: " + workingDir);
            return false;
        }

        // EDGE CASE: Strip package declarations so default-package compilation works.
        // Students sometimes include "package com.xxx;" which breaks flat compilation.
        stripPackageDeclarations(javaFiles);

        // Run javac
        return runJavac(workingDir, javaFiles);
    }

    /**
     * Compiles ONLY the specified target file + any tester files in the folder,
     * ignoring other .java files that may have errors (e.g. broken Q1a shouldn't
     * prevent Q1b from being compiled and graded).
     *
     * @param workingDir the folder containing the files
     * @param targetFile the student file to compile (e.g. "Q1b.java")
     * @return true if compilation succeeded
     */
    public boolean compileTargeted(Path workingDir, String targetFile) {
        if (workingDir == null || !Files.exists(workingDir)) {
            System.out.println("[Compiler] ❌ Directory does not exist: " + workingDir);
            return false;
        }

        List<Path> javaFiles = new ArrayList<>();

        // Always include the specific target file
        Path target = workingDir.resolve(targetFile);
        if (Files.exists(target))
            javaFiles.add(target);

        // Include any tester files (end with Tester.java)
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(workingDir, "*.java")) {
            for (Path f : stream) {
                String name = f.getFileName().toString();
                if (name.endsWith("Tester.java") && !javaFiles.contains(f)) {
                    javaFiles.add(f);
                }
            }
        } catch (IOException e) {
            System.out.println("[Compiler] ⚠️  Could not list directory: " + e.getMessage());
        }

        if (javaFiles.isEmpty()) {
            System.out.println("[Compiler] ❌ Target file not found: " + targetFile);
            return false;
        }

        stripPackageDeclarations(javaFiles);
        return runJavac(workingDir, javaFiles);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Collects all *.java files in the directory (non-recursive). */
    private List<Path> collectJavaFiles(Path dir) {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.java")) {
            for (Path f : stream)
                files.add(f);
        } catch (IOException e) {
            System.out.println("[Compiler] ⚠️  Could not list directory: " + e.getMessage());
        }
        return files;
    }

    /**
     * Removes "package ...;" lines from all Java files.
     *
     * WHY: Students may include package declarations. When we compile everything
     * flat in the same directory without the matching package folder structure,
     * javac fails with "class X is public, should be declared in a file named
     * X.java"
     * or similar cross-package errors.
     *
     * SAFE: Only the first occurrence of a line matching /^\s*package\s+.*;/ is
     * removed.
     * A backup is NOT made — this is a temp working folder that gets wiped each
     * run.
     */
    private void stripPackageDeclarations(List<Path> javaFiles) {
        for (Path file : javaFiles) {
            try {
                // Fall back to ISO-8859-1 if the file has non-UTF-8 bytes
                // (mirrors the same fix in StudentValidator.extractUsernameFromComments)
                List<String> lines;
                try {
                    lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                } catch (java.nio.charset.MalformedInputException e) {
                    lines = Files.readAllLines(file, java.nio.charset.StandardCharsets.ISO_8859_1);
                }
                boolean changed = false;

                for (int i = 0; i < lines.size(); i++) {
                    String trimmed = lines.get(i).trim();
                    if (trimmed.startsWith("package ") && trimmed.endsWith(";")) {
                        lines.set(i, "// [package declaration removed by auto-grader]");
                        changed = true;
                        break; // Only one package declaration per file
                    }
                }

                if (changed) {
                    Files.write(file, lines, StandardCharsets.UTF_8);
                }

            } catch (IOException e) {
                // Non-fatal — if we can't strip it, javac will show the error
                System.out.println("[Compiler] ⚠️  Could not strip package from "
                        + file.getFileName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Runs javac on all collected .java files.
     */
    private boolean runJavac(Path workingDir, List<Path> javaFiles) {
        try {
            String dirPath = workingDir.toAbsolutePath().toString();
            String classpath = buildClasspath(workingDir, dirPath);
            StringBuilder cmd = new StringBuilder();
            cmd.append("javac -cp \"").append(classpath)
                    .append("\" -d \"").append(dirPath)
                    .append("\" -encoding UTF-8 -nowarn");

            for (Path f : javaFiles) {
                cmd.append(" \"").append(f.toAbsolutePath()).append("\"");
            }

            ProcessBuilder pb = new ProcessBuilder();
            if (isWindows()) {
                pb.command("cmd", "/c", cmd.toString());
            } else {
                pb.command("sh", "-c", cmd.toString());
            }
            pb.directory(workingDir.toFile());

            Process process = pb.start();

            // --- CHANGED: Capture full error text instead of just a count ---
            String fullErrorReport = captureFullErrors(process);

            boolean completed = process.waitFor(COMPILER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                System.out.println("[Compiler] ❌ Compilation timed out");
                return false;
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                System.out.println("[Compiler] ✅ Compilation Success");
                return true;
            } else {
                // --- CHANGED: Print the actual errors to the console/logs ---
                System.out.println("[Compiler] ❌ Compilation failed:");
                System.out.println(fullErrorReport);
                return false;
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Reads stderr and returns the full error message as a String.
     * This ensures we see "Missing DataException" instead of just a count.
     */
    private String captureFullErrors(Process process) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Indent the error for better readability in the logs
                sb.append("    [javac] ").append(line).append("\n");
            }
        } catch (IOException e) {
            return "Could not read error stream: " + e.getMessage();
        }
        return sb.toString();
    }

    /**
     * Builds the javac classpath string.
     *
     * STANDARD (Q1–Q3): just the workingDir, so pre-compiled .class files
     * (DataException.class, Shape.class, etc.) are found by the compiler.
     *
     * Q4 EXTRA: also appends any *.jar files found recursively under
     * workingDir/external/ so Apache Commons Collections is on the classpath.
     *
     * Example on Linux:
     * /path/to/Q4:/path/to/Q4/external/apache/commons-collections4-4.4.jar
     *
     * @param workingDir the student's question folder
     * @param dirPath    pre-computed absolute path string of workingDir
     * @return classpath string with OS-appropriate separator
     */
    private String buildClasspath(Path workingDir, String dirPath) {
        String sep = System.getProperty("path.separator"); // ":" Linux/Mac, ";" Windows
        StringBuilder cp = new StringBuilder(dirPath);

        Path externalDir = workingDir.resolve("external");
        if (Files.exists(externalDir)) {
            try (java.util.stream.Stream<Path> walk = Files.walk(externalDir)) {
                walk.filter(p -> p.toString().endsWith(".jar"))
                        .forEach(jar -> cp.append(sep).append(jar.toAbsolutePath()));
            } catch (IOException e) {
                System.out.println("[Compiler] ⚠️  Could not scan external/ for JARs: "
                        + e.getMessage());
            }
        }

        return cp.toString();
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}