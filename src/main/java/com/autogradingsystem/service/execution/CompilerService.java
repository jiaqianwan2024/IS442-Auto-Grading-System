package com.autogradingsystem.service.execution;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.file.Path;

/**
 * CompilerService - Compiles Java files
 * 
 * VERSION: 2.0 (Phase 3 - Suppressed Error Output)
 * 
 * NEW IN v2.0:
 * - FIX 2: Compilation errors no longer flood the output
 * - Shows concise summary instead of 30+ error lines
 * 
 * @author IS442 Team
 * @version 2.0
 */
public class CompilerService {

    /**
     * Compiles all Java files in the specified directory.
     * 
     * FIX 2: Now suppresses verbose error output for cleaner logs
     * 
     * @param workingDir The folder containing Java files to compile
     * @return true if compilation succeeded, false otherwise
     */
    public boolean compile(Path workingDir) {
        try {
            ProcessBuilder builder = new ProcessBuilder();
            
            // OS DETECTION LOGIC
            boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
            
            if (isWindows) {
                builder.command("cmd.exe", "/c", "javac *.java");
            } else {
                builder.command("sh", "-c", "javac *.java");
            }

            builder.directory(workingDir.toFile());
            Process process = builder.start();

            // FIX 2: Capture errors silently instead of printing
            StringBuilder errorLog = new StringBuilder();
            BufferedReader errorReader = new BufferedReader(
                new InputStreamReader(process.getErrorStream())
            );
            
            String line;
            int errorCount = 0;
            
            while ((line = errorReader.readLine()) != null) {
                errorLog.append(line).append("\n");
                
                // Count actual error lines (not just warnings or notes)
                if (line.contains(" error:")) {
                    errorCount++;
                }
            }

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                System.out.println("[Compiler] ✅ Compilation Success");
                return true;
            } else {
                // FIX 2: Show concise summary instead of full error dump
                if (errorCount > 0) {
                    System.err.println("   [Compiler] ❌ Compilation failed (" + errorCount + " error(s))");
                } else {
                    System.err.println("   [Compiler] ❌ Compilation failed");
                }
                
                return false;
            }

        } catch (IOException | InterruptedException e) {
            System.err.println("   [Compiler] ❌ Exception: " + e.getMessage());
            return false;
        }
    }
}