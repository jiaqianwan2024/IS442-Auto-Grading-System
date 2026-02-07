package com.autogradingsystem.service.execution;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

public class CompilerService {

    /**
     * Compiles all Java files in the specified directory.
     * @param workingDir The folder containing Q1a.java and Q1aTester.java
     * @return true if compilation succeeded (created .class files), false otherwise.
     */
    public boolean compile(java.nio.file.Path workingDir) {
        try {
            ProcessBuilder builder = new ProcessBuilder();
            
            // -----------------------------------------------------
            // OS DETECTION LOGIC
            // Java commands behave differently on Windows vs Mac.
            // -----------------------------------------------------
            boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
            
            if (isWindows) {
                // Windows requires "cmd /c" to run terminal commands
                builder.command("cmd.exe", "/c", "javac *.java");
            } else {
                // Mac/Linux requires "sh -c" to understand the "*" wildcard.
                // If you just ran "javac *.java" directly without "sh", Java would look
                // for a file literally named "*.java" and fail.
                builder.command("sh", "-c", "javac *.java");
            }

            // Set the "Current Directory" to the student's folder.
            // This ensures javac compiles the files inside /tmp/.../student1/Q1
            builder.directory(workingDir.toFile());
            
            // Execute the command
            Process process = builder.start();

            // -----------------------------------------------------
            // DEBUGGING: ERROR CAPTURE
            // -----------------------------------------------------
            // This block reads the error stream from the compiler.
            // If the student has a syntax error, "javac" prints it here.
            // We capture it and print it to your console so you know WHY it failed.
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            String line;
            while ((line = errorReader.readLine()) != null) {
                System.err.println("   [javac ERROR]: " + line);
            }

            // Wait for the compiler to finish its job.
            int exitCode = process.waitFor();

            // Exit Code 0 means "Success" (No errors).
            // Any other number (usually 1) means "Error".
            if (exitCode == 0) {
                System.out.println("[Compiler] Compilation Success");
                return true;
            } else {
                System.out.println("[Compiler] Compilation Failed (Exit Code: " + exitCode + ")");
                return false;
            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }
}