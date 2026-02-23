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

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Collects all *.java files in the directory (non-recursive). */
    private List<Path> collectJavaFiles(Path dir) {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.java")) {
            for (Path f : stream) files.add(f);
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
     * javac fails with "class X is public, should be declared in a file named X.java"
     * or similar cross-package errors.
     *
     * SAFE: Only the first occurrence of a line matching /^\s*package\s+.*;/ is removed.
     * A backup is NOT made — this is a temp working folder that gets wiped each run.
     */
    private void stripPackageDeclarations(List<Path> javaFiles) {
        for (Path file : javaFiles) {
            try {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
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
     *
     * FLAGS:
     * -d {dir}         → output .class files into same directory
     * -encoding UTF-8  → handle non-ASCII identifiers/comments (edge case)
     * -nowarn          → suppress warnings, show only errors
     */
    private boolean runJavac(Path workingDir, List<Path> javaFiles) {
        try {
            // Build command: javac -d <dir> -encoding UTF-8 -nowarn <file1> <file2> ...
            String dirPath = workingDir.toAbsolutePath().toString();
            StringBuilder cmd = new StringBuilder();
            cmd.append("javac -d \"").append(dirPath)
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

            // Capture errors (suppressed from console — FIX 2)
            int errorCount = captureErrors(process);

            // EDGE CASE: Compiler hangs (e.g., annotation processor loops)
            boolean completed = process.waitFor(COMPILER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                System.out.println("[Compiler] ❌ Compilation timed out after "
                        + COMPILER_TIMEOUT_SECONDS + "s");
                return false;
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                System.out.println("[Compiler] ✅ Compilation Success");
                return true;
            } else {
                System.out.println("[Compiler] ❌ Compilation failed (" + errorCount + " error(s))");
                return false;
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // restore flag
            System.out.println("[Compiler] ❌ Compilation interrupted");
            return false;
        } catch (IOException e) {
            System.out.println("[Compiler] ❌ Compilation error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Reads stderr from the compiler process and counts error lines.
     * Output is NOT printed (FIX 2 — suppress error flood).
     */
    private int captureErrors(Process process) {
        int errorCount = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("error:") || line.contains("Error:")) {
                    errorCount++;
                }
            }
        } catch (IOException e) {
            // Best-effort
        }
        return Math.max(errorCount, 1);
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}