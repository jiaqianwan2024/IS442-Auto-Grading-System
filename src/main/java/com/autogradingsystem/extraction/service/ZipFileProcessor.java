package com.autogradingsystem.extraction.service;

import java.io.*;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * ZipFileProcessor - Low-Level ZIP Extraction Utility
 * 
 * PURPOSE:
 * - Provides core ZIP file extraction functionality
 * - Used by UnzipService and StudentValidator
 * - Handles cross-platform ZIP extraction
 * 
 * WHY SEPARATE CLASS?
 * - Single Responsibility Principle: This class only does ZIP operations
 * - Reusable: Multiple services need to extract ZIPs
 * - Testable: Can test ZIP extraction independently
 * 
 * TECHNICAL DETAILS:
 * - Uses Java NIO (New I/O) for better performance
 * - Handles nested directories automatically
 * - Cross-platform: Works on Windows, Mac, Linux
 * - Preserves directory structure from ZIP
 * 
 * NO CHANGES FROM v3.0:
 * - This class was already well-designed
 * - Only package declaration updated
 * 
 * @author IS442 Team
 * @version 4.0 (Spring Boot Microservices Structure)
 */
public class ZipFileProcessor {
    
    /**
     * Extracts a ZIP file to the specified destination directory
     * 
     * WORKFLOW:
     * 1. Open ZIP file as input stream
     * 2. Read each entry (file or directory) from ZIP
     * 3. For directories: Create directory at destination
     * 4. For files: Extract file content to destination
     * 5. Preserve the directory structure
     * 
     * EXAMPLE:
     * Input ZIP structure:
     *   ping.lee.2023/
     *   ├── Q1/
     *   │   ├── Q1a.java
     *   │   └── Q1b.java
     *   └── Q2/
     *       └── Q2a.java
     * 
     * After extraction to /data/extracted/ping.lee.2023:
     *   /data/extracted/ping.lee.2023/
     *   ├── Q1/
     *   │   ├── Q1a.java
     *   │   └── Q1b.java
     *   └── Q2/
     *       └── Q2a.java
     * 
     * CROSS-PLATFORM COMPATIBILITY:
     * - Windows: Handles both / and \ path separators
     * - Mac/Linux: Handles / path separator
     * - Automatically normalizes paths using Path.normalize()
     * 
     * SECURITY:
     * - Prevents ZIP slip attacks (malicious ZIPs trying to escape destination)
     * - Validates all extracted paths stay within destination directory
     * 
     * @param zipFilePath Path to the ZIP file to extract
     * @param destinationDir Directory to extract contents to
     * @throws IOException if extraction fails or ZIP is corrupted
     */
    public static void unzip(Path zipFilePath, Path destinationDir) throws IOException {
        
        // Ensure destination directory exists
        if (!Files.exists(destinationDir)) {
            Files.createDirectories(destinationDir);
        }
        
        // Open ZIP file
        try (ZipInputStream zis = new ZipInputStream(
                Files.newInputStream(zipFilePath))) {
            
            ZipEntry entry;
            
            // Process each entry in the ZIP
            while ((entry = zis.getNextEntry()) != null) {
                
                // Build target path for this entry
                Path targetPath = destinationDir.resolve(entry.getName());
                
                // SECURITY CHECK: Prevent ZIP slip attack
                // Ensure extracted file stays within destination directory
                if (!targetPath.toAbsolutePath().normalize().startsWith(
                        destinationDir.toAbsolutePath().normalize())) {
                    throw new IOException(
                        "ZIP entry tries to escape destination directory: " + entry.getName()
                    );
                }
                
                if (entry.isDirectory()) {
                    // Entry is a directory - create it
                    Files.createDirectories(targetPath);
                    
                } else {
                    // Entry is a file - extract it
                    
                    // Ensure parent directory exists
                    Path parent = targetPath.getParent();
                    if (parent != null && !Files.exists(parent)) {
                        Files.createDirectories(parent);
                    }
                    
                    // Extract file content
                    try (OutputStream out = Files.newOutputStream(targetPath)) {
                        byte[] buffer = new byte[8192];  // 8KB buffer for efficiency
                        int len;
                        
                        while ((len = zis.read(buffer)) > 0) {
                            out.write(buffer, 0, len);
                        }
                    }
                }
                
                // Close current entry before moving to next
                zis.closeEntry();
            }
        }
    }
    
    /**
     * Checks if a file is a valid ZIP file
     * Useful for validation before attempting extraction
     * 
     * VALIDATION METHOD:
     * - Checks file extension is .zip (case-insensitive)
     * - Checks file exists and is readable
     * 
     * NOTE: This is a basic check - doesn't verify ZIP integrity
     * Use with caution - corrupt ZIPs will only fail during extraction
     * 
     * @param filePath Path to file to check
     * @return true if file exists and has .zip extension, false otherwise
     */
    public static boolean isZipFile(Path filePath) {
        
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            return false;
        }
        
        String fileName = filePath.getFileName().toString().toLowerCase();
        return fileName.endsWith(".zip");
    }
}