package com.autogradingsystem.execution.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * CompilerService - Compiles Java Files in a Student Folder
 *
 * PURPOSE:
 * - Compiles all .java files in a directory
 * - Strips package declarations before compilation
 * - Provides concise compilation status
 * - Cross-platform support (Windows/Mac/Linux)
 *
 * CHANGES IN v3.2:
 * - Added CompileResult inner class to carry both success flag and stripped-package file list
 * - Added compileTargetedWithDetails() so ExecutionController can flag WrongPackage in remarks
 * - stripPackageDeclarations() now returns list of files where a package was stripped
 */
public class CompilerService {

    private static final int COMPILER_TIMEOUT_SECONDS = 30;

    // ── Inner class ───────────────────────────────────────────────────────────

    /**
     * Carries the result of a targeted compilation.
     * success                — whether javac exited 0
     * strippedPackageFiles   — student files (not testers) that had a package declaration removed
     */
    public static class CompileResult {
        public final boolean      success;
        public final List<String> strippedPackageFiles;

        public CompileResult(boolean success, List<String> strippedPackageFiles) {
            this.success              = success;
            this.strippedPackageFiles = Collections.unmodifiableList(strippedPackageFiles);
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Compiles all .java files in the given directory.
     * Used for script/folder tasks (e.g. Q4).
     *
     * @param workingDir Directory containing .java files
     * @return true if compilation succeeded
     */
    public boolean compile(Path workingDir) {
        if (workingDir == null || !Files.exists(workingDir)) {
            System.out.println("[Compiler] ❌ Directory does not exist: " + workingDir);
            return false;
        }

        List<Path> javaFiles = collectJavaFiles(workingDir);
        if (javaFiles.isEmpty()) {
            System.out.println("[Compiler] ❌ No .java files found in: " + workingDir);
            return false;
        }

        stripPackageDeclarations(javaFiles);
        return runJavac(workingDir, javaFiles);
    }

    /**
     * Compiles ONLY the specified target file + tester files.
     * Simple boolean version — use when package-flag remarks are not needed
     * (e.g. script tasks).
     *
     * @param workingDir Directory containing the files
     * @param targetFile Student file to compile (e.g. "Q1b.java")
     * @return true if compilation succeeded
     */
    public boolean compileTargeted(Path workingDir, String targetFile) {
        return compileTargetedWithDetails(workingDir, targetFile).success;
    }

    /**
     * Compiles ONLY the specified target file + tester files.
     * Returns a CompileResult so the caller can detect WrongPackage situations.
     *
     * @param workingDir Directory containing the files
     * @param targetFile Student file to compile (e.g. "Q1b.java")
     * @return CompileResult with success flag and list of files that had packages stripped
     */
    public CompileResult compileTargetedWithDetails(Path workingDir, String targetFile) {
        if (workingDir == null || !Files.exists(workingDir)) {
            System.out.println("[Compiler] ❌ Directory does not exist: " + workingDir);
            return new CompileResult(false, Collections.emptyList());
        }

        List<Path> javaFiles = new ArrayList<>();

        // Always include the specific target file
        Path target = workingDir.resolve(targetFile);
        if (Files.exists(target)) javaFiles.add(target);

        // Include tester files
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
            return new CompileResult(false, Collections.emptyList());
        }

        // Strip packages and collect which student files (not testers) were affected
        List<String> stripped = stripPackageDeclarations(javaFiles);
        // Filter out tester files from the stripped list — we only want to flag student files
        List<String> studentStripped = new ArrayList<>();
        for (String f : stripped) {
            if (!f.endsWith("Tester.java")) studentStripped.add(f);
        }

        boolean success = runJavac(workingDir, javaFiles);
        return new CompileResult(success, studentStripped);
    }

    /**
     * Compiles the target student file plus any non-tester Java helpers in the same folder.
     * This is used as a fallback when an injected tester is incompatible but the student's
     * own file contains a runnable self-test main method.
     */
    public CompileResult compileStudentSourcesWithDetails(Path workingDir, String targetFile) {
        if (workingDir == null || !Files.exists(workingDir)) {
            System.out.println("[Compiler] ❌ Directory does not exist: " + workingDir);
            return new CompileResult(false, Collections.emptyList());
        }

        List<Path> javaFiles = new ArrayList<>();
        Path target = workingDir.resolve(targetFile);
        if (Files.exists(target)) {
            javaFiles.add(target);
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(workingDir, "*.java")) {
            for (Path f : stream) {
                String name = f.getFileName().toString();
                if (!name.endsWith("Tester.java") && !javaFiles.contains(f)) {
                    javaFiles.add(f);
                }
            }
        } catch (IOException e) {
            System.out.println("[Compiler] ⚠️  Could not list directory: " + e.getMessage());
        }

        if (javaFiles.isEmpty()) {
            System.out.println("[Compiler] ❌ Target file not found: " + targetFile);
            return new CompileResult(false, Collections.emptyList());
        }

        List<String> stripped = stripPackageDeclarations(javaFiles);
        List<String> studentStripped = new ArrayList<>();
        for (String f : stripped) {
            if (!f.endsWith("Tester.java")) {
                studentStripped.add(f);
            }
        }

        boolean success = runJavac(workingDir, javaFiles);
        return new CompileResult(success, studentStripped);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

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
     * Returns the list of filenames (not full paths) where a package was removed.
     *
     * WHY: Students may include package declarations. Flat compilation without the
     * matching folder structure causes javac errors.
     *
     * SAFE: Only the first occurrence of /^\s*package\s+.*;/ is removed per file.
     */
    private List<String> stripPackageDeclarations(List<Path> javaFiles) {
        List<String> stripped = new ArrayList<>();
        for (Path file : javaFiles) {
            try {
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
                        break;
                    }
                }

                if (changed) {
                    Files.write(file, lines, StandardCharsets.UTF_8);
                    stripped.add(file.getFileName().toString());
                }

            } catch (IOException e) {
                System.out.println("[Compiler] ⚠️  Could not strip package from "
                        + file.getFileName() + ": " + e.getMessage());
            }
        }
        return stripped;
    }

    private boolean runJavac(Path workingDir, List<Path> javaFiles) {
        try {
            String dirPath  = workingDir.toAbsolutePath().toString();
            String classpath = buildClasspath(workingDir, dirPath);

            StringBuilder cmd = new StringBuilder();
            cmd.append("javac -cp \"").append(classpath)
               .append("\" -d \"").append(dirPath)
               .append("\" -encoding UTF-8 -nowarn");
            for (Path f : javaFiles)
                cmd.append(" \"").append(f.toAbsolutePath()).append("\"");

            ProcessBuilder pb = new ProcessBuilder();
            if (isWindows()) pb.command("cmd", "/c", cmd.toString());
            else             pb.command("sh",  "-c", cmd.toString());
            pb.directory(workingDir.toFile());

            Process process = pb.start();
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

    private String captureFullErrors(Process process) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = reader.readLine()) != null)
                sb.append("    [javac] ").append(line).append("\n");
        } catch (IOException e) {
            return "Could not read error stream: " + e.getMessage();
        }
        return sb.toString();
    }

    private String buildClasspath(Path workingDir, String dirPath) {
        String sep = System.getProperty("path.separator");
        StringBuilder cp = new StringBuilder(dirPath);

        Path externalDir = workingDir.resolve("external");
        if (Files.exists(externalDir)) {
            try (java.util.stream.Stream<Path> walk = Files.walk(externalDir)) {
                walk.filter(p -> p.toString().endsWith(".jar"))
                    .forEach(jar -> cp.append(sep).append(jar.toAbsolutePath()));
            } catch (IOException e) {
                System.out.println("[Compiler] ⚠️  Could not scan external/ for JARs: " + e.getMessage());
            }
        }
        return cp.toString();
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
