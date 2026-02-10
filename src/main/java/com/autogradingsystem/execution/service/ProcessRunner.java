package com.autogradingsystem.execution.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * ProcessRunner - Executes Compiled Java Code
 * 
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
    
    /**
     * Timeout duration in seconds
     * Prevents infinite loops from hanging the system
     */
    private static final int TIMEOUT_SECONDS = 5;
    
    /**
     * Executes tester class and captures output
     * 
     * COMMAND EXECUTED:
     * java -cp {workingDir} {testerClassName}
     * 
     * EXAMPLE:
     * java -cp /data/extracted/ping.lee.2023/Q1 Q1aTester
     * 
     * OUTPUT HANDLING:
     * - Merges stdout and stderr into single output
     * - Captures all print statements from tester
     * - Returns complete output as string
     * 
     * TIMEOUT:
     * - Waits maximum 5 seconds for completion
     * - If timeout → kills process and returns "TIMEOUT" message
     * - Prevents infinite loops from blocking system
     * 
     * @param testerClassName Tester class name (e.g., "Q1aTester")
     * @param workingDir Directory containing compiled .class files
     * @return Complete output from execution, or "TIMEOUT" message
     */
    public String runTester(String testerClassName, Path workingDir) {
        
        try {
            // Build java command
            ProcessBuilder pb = new ProcessBuilder();
            
            if (isWindows()) {
                // Windows: cmd /c java ...
                pb.command(
                    "cmd", "/c",
                    "java", "-cp", workingDir.toAbsolutePath().toString(), testerClassName
                );
            } else {
                // Mac/Linux: java ...
                pb.command(
                    "java", "-cp", workingDir.toAbsolutePath().toString(), testerClassName
                );
            }
            
            pb.directory(workingDir.toFile());
            
            // Merge stdout and stderr (capture all output)
            pb.redirectErrorStream(true);
            
            // Start process
            Process process = pb.start();
            
            // Capture output
            StringBuilder output = new StringBuilder();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            // Wait for completion (with timeout)
            boolean completed = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            
            if (!completed) {
                // Timeout - kill process
                process.destroyForcibly();
                return "TIMEOUT: Execution exceeded " + TIMEOUT_SECONDS + " seconds\n" +
                       "Possible infinite loop in student code";
            }
            
            // Check exit code
            int exitCode = process.exitValue();
            
            if (exitCode != 0) {
                // Non-zero exit code - append error message
                output.append("\n[Process exited with code ").append(exitCode).append("]");
            }
            
            return output.toString();
            
        } catch (IOException | InterruptedException e) {
            return "ERROR: Failed to run tester\n" + e.getMessage();
        }
    }
    
    /**
     * Checks if running on Windows
     * 
     * USED FOR:
     * - Choosing correct command format
     * - Windows uses: cmd /c java
     * - Mac/Linux uses: java directly
     * 
     * @return true if Windows, false otherwise
     */
    private boolean isWindows() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("win");
    }
}