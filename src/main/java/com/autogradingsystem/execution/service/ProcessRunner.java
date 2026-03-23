package com.autogradingsystem.execution.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * ProcessRunner - Executes Compiled Java Code
 * PURPOSE:
 * - Runs tester classes to execute student code
 * - Captures all console output (stdout + stderr)
 * - Enforces timeout to prevent infinite loops
 * - Cross-platform support (Windows/Mac/Linux)
 * * WORKFLOW:
 * 1. Build java command to run tester class
 * 2. Execute java process
 * 3. Capture all output (stdout + stderr merged)
 * 4. Wait for completion (max 5 seconds)
 * 5. Return captured output
 * * TIMEOUT PROTECTION & PARTIAL CREDIT RESCUE:
 * - 10-second timeout prevents infinite loops
 * - Uses thread-safe StringBuffer to rescue flushed output before process death
 * - Bypasses Windows "cmd /c" to ensure JVM is strictly
 */
public class ProcessRunner {

    /** Timeout in seconds before killing student process */
    private static final int TIMEOUT_SECONDS = 10;

    /** Max output lines captured — prevents memory issues from print-heavy code */
    private static final int MAX_OUTPUT_LINES = 500;

    /** Max JVM heap for student process — prevents memory hog */
    private static final String MAX_HEAP = "-Xmx128m";

    /**
     * Executes a tester class in the given working directory and returns its output.
     *
     * @param testerClassName Class name to run (e.g., "Q1aTester")
     * @param workingDir      Directory containing compiled .class files
     * @return Full captured output, a TIMEOUT message, or an ERROR message
     */
    public String runTester(String testerClassName, Path workingDir) {
        return runTester(testerClassName, workingDir, null);
    }

    /**
     * Executes a tester class with an optional extra argument (used by Q4Tester
     * which expects the student's Q4 folder path as args[0]).
     *
     * @param testerClassName Class name to run (e.g., "Q4Tester")
     * @param workingDir      Directory containing compiled .class files
     * @param extraArg        Optional argument passed to the tester's main(args) — may be null
     * @return Full captured output, a TIMEOUT message, or an ERROR message
     */
    public String runTester(String testerClassName, Path workingDir, String extraArg) {

        // Guard: null or missing working directory
        if (workingDir == null || !workingDir.toFile().exists()) {
            return "ERROR: Working directory does not exist: " + workingDir;
        }

        // Guard: blank class name
        if (testerClassName == null || testerClassName.isBlank()) {
            return "ERROR: Tester class name is null or empty";
        }

        Process process = null;
        ExecutorService readerPool = Executors.newSingleThreadExecutor();

        try {
            ProcessBuilder pb = buildProcessCommand(testerClassName, workingDir, extraArg);
            pb.redirectErrorStream(true); // merge stderr into stdout
            process = pb.start();

            // FIX 1: Use a thread-safe StringBuffer OUTSIDE the reader thread.
            // This guarantees that if the thread crashes on process kill, the text survives.
            StringBuffer sharedOutput = new StringBuffer();
            final Process finalProcess = process;
            
            // Start reading asynchronously
            Future<?> outputFuture = readerPool.submit(() -> readOutput(finalProcess, sharedOutput));

            // Wait with timeout
            boolean completed = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!completed) {
                // EDGE CASE: Infinite loop — kill process, but RESCUE the partial output
                process.destroyForcibly();
                
                // Give the OS 500ms to flush the final bytes into our StringBuffer before returning
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                
                return sharedOutput.toString() + "\n[SYSTEM] TIMEOUT: Execution exceeded time limit.";
            }

            // Normal Completion: Collect output (give reader up to 2 extra seconds to finish draining)
            try {
                outputFuture.get(2, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                sharedOutput.append("\n[Output reader timed out — partial output may be lost]");
            }

            // Append non-zero exit code info
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                sharedOutput.append("\n[Process exited with code ").append(exitCode).append("]");
            }

            return sharedOutput.toString();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) process.destroyForcibly();
            return "ERROR: Grading was interrupted.\n" + e.getMessage();

        } catch (IOException e) {
            return "ERROR: Failed to start process.\n" + e.getMessage();

        } catch (Exception e) {
            if (process != null) process.destroyForcibly();
            return "ERROR: Unexpected error during execution.\n" + e.getMessage();

        } finally {
            readerPool.shutdownNow();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds the ProcessBuilder command.
     * Sets memory cap via -Xmx to prevent student code from consuming all RAM.
     * Includes any external JARs found in an external/ subfolder (needed for Q4).
     * Passes extraArg to the tester's main(args) when provided (needed for Q4Tester).
     */
    private ProcessBuilder buildProcessCommand(String testerClassName, Path workingDir, String extraArg) {
        String cp = buildClasspath(workingDir);
        ProcessBuilder pb = new ProcessBuilder();

        // Build command list dynamically so extraArg can be optionally appended
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add("java");
        cmd.add(MAX_HEAP);
        cmd.add("-cp");
        cmd.add(cp);
        cmd.add(testerClassName);

        // Q4Tester needs the student's Q4 folder path passed as args[0]
        // so it knows where to find compile.sh / run.sh
        if (extraArg != null && !extraArg.isBlank()) {
            cmd.add(extraArg);
        }

        pb.command(cmd);
        pb.directory(workingDir.toFile());
        return pb;
    }

    /**
     * Builds the classpath string for the java command.
     *
     * STANDARD: just the workingDir (covers Q1–Q3).
     * Q4 EXTRA:  also appends any *.jar files found recursively under
     *            workingDir/external/ so Apache Commons is on the path.
     *
     * Example result on Linux:
     *   /path/to/Q4:/path/to/Q4/external/apache/commons-collections4-4.4.jar
     */
    private String buildClasspath(Path workingDir) {
        String sep = System.getProperty("path.separator"); // ":" on Mac/Linux, ";" on Windows
        StringBuilder cp = new StringBuilder(workingDir.toAbsolutePath().toString());

        // Look for external JARs (Q4 uses commons-collections4-4.4.jar)
        Path externalDir = workingDir.resolve("external");
        if (java.nio.file.Files.exists(externalDir)) {
            try (java.util.stream.Stream<java.nio.file.Path> walk =
                         java.nio.file.Files.walk(externalDir)) {
                walk.filter(p -> p.toString().endsWith(".jar"))
                    .forEach(jar -> cp.append(sep).append(jar.toAbsolutePath()));
            } catch (java.io.IOException e) {
                System.out.println("[ProcessRunner] ⚠️  Could not scan external/ for JARs: "
                        + e.getMessage());
            }
        }

        return cp.toString();
    }

    /**
     * Reads stdout (and merged stderr) from the process.
     * Caps at MAX_OUTPUT_LINES to avoid memory blow-up from chatty student code.
     * Modified to write to an external StringBuffer for crash-resilience.
     */
    private void readOutput(Process process, StringBuffer output) {
        int lineCount = 0;
        boolean truncated = false;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (lineCount >= MAX_OUTPUT_LINES) {
                    truncated = true;
                    // Drain the rest without storing — prevents buffer deadlock
                    //noinspection StatementWithEmptyBody
                    while (reader.readLine() != null) { }
                    break;
                }
                output.append(line).append("\n");
                lineCount++;
            }

        } catch (IOException e) {
            output.append("[Stream closed]");
        }

        if (truncated) {
            output.append("\n[Output truncated — exceeded ")
                  .append(MAX_OUTPUT_LINES)
                  .append(" lines]");
        }
    }
}