package com.autogradingsystem.execution.service;

import com.autogradingsystem.PathConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * TesterInjector - Copies Tester Files to Student Folders
 * 
 * PURPOSE:
 * - Copies tester files from resources/input/testers/ to student folders
 * - Enables student code and tester to be compiled together
 * - Supports both development (filesystem) and production (JAR) deployment
 * 
 * WHY NEEDED?
 * - Tester and student code must be in same directory to compile together
 * - Tester imports student's classes (e.g., Q1a.java)
 * - Java compiler needs both files in same location
 * 
 * WORKFLOW:
 * 1. Student code: data/extracted/ping.lee.2023/Q1/Q1a.java
 * 2. Tester location: resources/input/testers/Q1aTester.java
 * 3. Copy tester TO: data/extracted/ping.lee.2023/Q1/Q1aTester.java
 * 4. Now both in same folder → can compile together
 * 5. Run: java Q1aTester
 * 
 * CHANGES FROM v3.0:
 * - Updated to use PathConfig for tester directory
 * - No logging changes needed (silent operation)
 * - Cleaner error messages
 * 
 * @author IS442 Team
 * @version 4.0 (Spring Boot Microservices Structure)
 */
public class TesterInjector {
    
    /**
     * Copies tester file to student's question folder
     * 
     * DEPLOYMENT MODES:
     * 
     * Mode 1: Development (filesystem)
     * - Testers are in: resources/input/testers/Q1aTester.java
     * - Copy directly from filesystem
     * 
     * Mode 2: Production (JAR)
     * - Testers are in: JAR:/resources/input/testers/Q1aTester.java
     * - Extract from JAR using classpath loading
     * 
     * AUTOMATICALLY DETECTS:
     * - Tries filesystem first
     * - Falls back to classpath if file not on filesystem
     * - Works in both development and production
     * 
     * @param testerFile Tester filename (e.g., "Q1aTester.java")
     * @param destinationFolder Student's question folder (e.g., data/extracted/ping.lee.2023/Q1/)
     * @throws IOException if tester cannot be copied
     */
    public void copyTester(String testerFile, Path destinationFolder) throws IOException {
        
        // Build source path in resources/input/testers/
        Path testerSource = PathConfig.INPUT_TESTERS.resolve(testerFile);
        
        // Build destination path in student's folder
        Path testerDestination = destinationFolder.resolve(testerFile);
        
        // Try filesystem copy first (development mode)
        if (Files.exists(testerSource)) {
            Files.copy(testerSource, testerDestination, StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        
        // Fallback: Load from classpath (production/JAR mode)
        String resourcePath = "testers/" + testerFile;
        
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            
            if (inputStream == null) {
                throw new IOException(
                    "Tester file not found: " + testerFile + "\n" +
                    "Searched in:\n" +
                    "  - Filesystem: " + testerSource + "\n" +
                    "  - Classpath: " + resourcePath + "\n" +
                    "Please ensure tester exists in resources/input/testers/"
                );
            }
            
            // Copy from JAR to filesystem
            Files.copy(inputStream, testerDestination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
    
    /**
     * Checks if a tester file exists
     * 
     * USEFUL FOR:
     * - Validation before copying
     * - Checking tester availability
     * 
     * @param testerFile Tester filename to check
     * @return true if tester exists (filesystem or classpath), false otherwise
     */
    public boolean testerExists(String testerFile) {
        
        // Check filesystem first
        Path testerPath = PathConfig.INPUT_TESTERS.resolve(testerFile);
        if (Files.exists(testerPath)) {
            return true;
        }
        
        // Check classpath
        String resourcePath = "testers/" + testerFile;
        InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath);
        
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException e) {
                // Ignore
            }
            return true;
        }
        
        return false;
    }
}