package com.autogradingsystem.execution.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;

/**
 * CompilerService - Compiles Java Files
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
 * 
 * @author IS442 Team
 * @version 4.0 (Spring Boot Microservices Structure)
 */
public class CompilerService {
    
    /**
     * Compiles all .java files in the specified directory
     * 
     * COMMAND EXECUTED:
     * javac -d {workingDir} {workingDir}/*.java
     * 
     * FLAGS:
     * -d {workingDir} : Output .class files to same directory
     * 
     * CROSS-PLATFORM:
     * - Windows: Uses "cmd /c javac ..."
     * - Mac/Linux: Uses "javac ..." directly
     * 
     * ERROR HANDLING:
     * - Errors are captured but not displayed (FIX 2)
     * - Returns false if compilation fails
     * - Calling code logs concise failure message
     * 
     * @param workingDir Directory containing .java files
     * @return true if compilation succeeded, false if failed
     */
    public boolean compile(Path workingDir) {
        
        try {
            // Build javac command
            String javacCommand = buildJavacCommand(workingDir);
            
            // Execute compilation
            ProcessBuilder pb = new ProcessBuilder();
            
            if (isWindows()) {
                // Windows: cmd /c javac ...
                pb.command("cmd", "/c", javacCommand);
            } else {
                // Mac/Linux: javac ...
                pb.command("sh", "-c", javacCommand);
            }
            
            pb.directory(workingDir.toFile());
            
            // Start process
            Process process = pb.start();
            
            // Capture error output (but don't display it - FIX 2)
            int errorCount = captureErrors(process);
            
            // Wait for completion
            int exitCode = process.waitFor();
            
            // Log compilation result
            if (exitCode == 0) {
                System.out.println("[Compiler] ✅ Compilation Success");
                return true;
            } else {
                // Concise error message (FIX 2 - no error flood)
                System.out.println("[Compiler] ❌ Compilation failed (" + errorCount + " error(s))");
                return false;
            }
            
        } catch (IOException | InterruptedException e) {
            System.out.println("[Compiler] ❌ Compilation error: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Builds the javac command for compiling all .java files
     * 
     * COMMAND FORMAT:
     * javac -d {directory} {directory}/*.java
     * 
     * EXAMPLE:
     * javac -d /data/extracted/ping.lee.2023/Q1 /data/extracted/ping.lee.2023/Q1/*.java
     * 
     * @param workingDir Directory containing .java files
     * @return javac command string
     */
    private String buildJavacCommand(Path workingDir) {
        
        String dirPath = workingDir.toAbsolutePath().toString();
        
        // Build command: javac -d {dir} {dir}/*.java
        return "javac -d \"" + dirPath + "\" \"" + dirPath + "\"/*.java";
    }
    
    /**
     * Captures error output and counts errors
     * 
     * ERROR SUPPRESSION (FIX 2):
     * - Reads all error output from stderr
     * - Counts number of error lines
     * - Does NOT print errors to console
     * - Prevents error flood
     * 
     * WHY SUPPRESS?
     * - Student syntax errors can generate 30+ error lines
     * - Floods console and makes output unreadable
     * - We only need to know: compiled or failed
     * - Detailed errors not helpful for automated grading
     * 
     * @param process Compilation process
     * @return Number of error lines
     */
    private int captureErrors(Process process) {
        
        int errorCount = 0;
        
        try (BufferedReader errorReader = new BufferedReader(
                new InputStreamReader(process.getErrorStream()))) {
            
            String line;
            while ((line = errorReader.readLine()) != null) {
                // Count errors but don't print them (FIX 2)
                if (line.contains("error:") || line.contains("Error:")) {
                    errorCount++;
                }
            }
            
        } catch (IOException e) {
            // Ignore - best effort counting
        }
        
        return errorCount > 0 ? errorCount : 1;  // At least 1 error if failed
    }
    
    /**
     * Checks if running on Windows
     * 
     * USED FOR:
     * - Choosing correct command format
     * - Windows uses: cmd /c javac
     * - Mac/Linux uses: sh -c javac
     * 
     * @return true if Windows, false otherwise
     */
    private boolean isWindows() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("win");
    }
}