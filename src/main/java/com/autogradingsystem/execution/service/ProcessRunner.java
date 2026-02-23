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
 * 
 * WORKFLOW:
 * 1. Build java command to run tester class
 * 2. Execute java process
 * 3. Capture all output (stdout + stderr merged)
 * 4. Wait for completion (max 5 seconds)
 * 5. Return captured output
 * 
 * TIMEOUT PROTECTION:
 * - 5-second timeout prevents infinite loops
 * - If student code has while(true), timeout kills process
 * - Returns "TIMEOUT" message instead of hanging forever
 * 
 * NO CHANGES FROM v3.0:
 * - Already well-designed
 * - Package declaration updated
 * - No logic changes needed
 * 
 * @author IS442 Team
 * @version 4.0 (Spring Boot Microservices Structure)
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
     * EDGE CASES:
     * 1. Output stream deadlock — stdout is read asynchronously via a separate thread,
     *    so a process that fills its output buffer won't deadlock against waitFor().
     * 2. Infinite loop — process is destroyed after TIMEOUT_SECONDS.
     * 3. Large output — capped at MAX_OUTPUT_LINES to avoid OOM.
     * 4. Memory abuse — JVM is launched with -Xmx128m.
     * 5. Non-zero exit code — appended to output for visibility.
     * 6. Null workingDir — returns error immediately.
     *
     * @param testerClassName Class name to run (e.g., "Q1aTester")
     * @param workingDir      Directory containing compiled .class files
     * @return Full captured output, a TIMEOUT message, or an ERROR message
     */
    public String runTester(String testerClassName, Path workingDir) {

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
            ProcessBuilder pb = buildProcessCommand(testerClassName, workingDir);
            pb.redirectErrorStream(true); // merge stderr into stdout
            process = pb.start();

            // EDGE CASE: Read output ASYNCHRONOUSLY to prevent output-buffer deadlock.
            // If the student's code produces a lot of output and we call waitFor() before
            // draining the stream, the child process blocks trying to write — deadlock.
            final Process finalProcess = process;
            Future<String> outputFuture = readerPool.submit(() -> readOutput(finalProcess));

            // Wait with timeout
            boolean completed = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!completed) {
                // EDGE CASE: Infinite loop or hanging I/O — kill everything
                process.destroyForcibly();
                outputFuture.cancel(true);
                return "TIMEOUT: Execution exceeded " + TIMEOUT_SECONDS + " seconds.\n"
                        + "Possible cause: infinite loop or blocking I/O in student code.";
            }

            // Collect output (give reader up to 2 extra seconds to finish draining)
            String output;
            try {
                output = outputFuture.get(2, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                output = "[Output reader timed out — partial output may be lost]";
            }

            // Append non-zero exit code info
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                output += "\n[Process exited with code " + exitCode + "]";
            }

            return output;

        } catch (InterruptedException e) {
            // EDGE CASE: The grading thread itself was interrupted (e.g., shutdown signal).
            // Restore the interrupt flag so callers can detect it.
            Thread.currentThread().interrupt();
            if (process != null) process.destroyForcibly();
            return "ERROR: Grading was interrupted.\n" + e.getMessage();

        } catch (IOException e) {
            return "ERROR: Failed to start process.\n" + e.getMessage();

        } catch (Exception e) {
            if (process != null) process.destroyForcibly();
            return "ERROR: Unexpected error during execution.\n" + e.getMessage();

        } finally {
            // Always shut down the reader thread pool
            readerPool.shutdownNow();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds the ProcessBuilder command.
     * Sets memory cap via -Xmx to prevent student code from consuming all RAM.
     */
    private ProcessBuilder buildProcessCommand(String testerClassName, Path workingDir) {
        String cp = workingDir.toAbsolutePath().toString();
        ProcessBuilder pb = new ProcessBuilder();

        if (isWindows()) {
            pb.command("cmd", "/c", "java", MAX_HEAP, "-cp", cp, testerClassName);
        } else {
            pb.command("java", MAX_HEAP, "-cp", cp, testerClassName);
        }

        pb.directory(workingDir.toFile());
        return pb;
    }

    /**
     * Reads stdout (and merged stderr) from the process.
     * Caps at MAX_OUTPUT_LINES to avoid memory blow-up from chatty student code.
     * Meant to run in a separate thread.
     */
    private String readOutput(Process process) {
        StringBuilder output = new StringBuilder();
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
            output.append("[Output read error: ").append(e.getMessage()).append("]");
        }

        if (truncated) {
            output.append("\n[Output truncated — exceeded ")
                  .append(MAX_OUTPUT_LINES)
                  .append(" lines]");
        }

        return output.toString();
    }

    /** @return true if the current OS is Windows */
    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}