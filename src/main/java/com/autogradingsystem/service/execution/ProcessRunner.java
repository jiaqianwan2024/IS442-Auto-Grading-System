package com.autogradingsystem.service.execution;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class ProcessRunner {

    /**
     * Executes a compiled Java class in a separate process.
     * * @param testerClassName The name of the class to run (e.g., "Q1aTester").
     * Do NOT include ".java" or ".class" here.
     * @param workingDir      The folder where the .class files are located.
     * @return A String containing the console output (STDOUT) of the execution.
     */
    public String runTester(String testerClassName, java.nio.file.Path workingDir) {
        StringBuilder output = new StringBuilder();
        try {
            // -----------------------------------------------------
            // 1. CONSTRUCT THE COMMAND
            // -----------------------------------------------------
            // Equivalent to typing: "java Q1aTester" in the terminal.
            ProcessBuilder builder = new ProcessBuilder("java", testerClassName);
            
            // Set the execution context to the student's folder.
            // This ensures the JVM finds the student's compiled .class files.
            builder.directory(workingDir.toFile());
            
            // MERGE STREAMS:
            // This combines System.out (normal text) and System.err (red error text)
            // into a single stream. This ensures we capture runtime exceptions
            // (like NullPointerException) in our log.
            builder.redirectErrorStream(true);

            // -----------------------------------------------------
            // 2. START THE PROCESS
            // -----------------------------------------------------
            Process process = builder.start();

            // -----------------------------------------------------
            // 3. SAFETY TIMEOUT (CRITICAL)
            // -----------------------------------------------------
            // Wait up to 5 seconds for the code to finish.
            // If it takes longer, "finished" becomes false.
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);

            if (!finished) {
                // The student has an infinite loop or very slow code.
                process.destroy(); // Try to kill politely
                process.destroyForcibly(); // Kill immediately
                return "Error: Execution Timed Out";
            }

            // -----------------------------------------------------
            // 4. CAPTURE OUTPUT
            // -----------------------------------------------------
            // Read the output line by line and store it in memory.
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        } catch (Exception e) {
            // Catches OS errors (e.g., cannot find "java" command)
            return "Error: " + e.getMessage();
        }
        
        // Return the full log to the Controller for parsing
        return output.toString();
    }
}