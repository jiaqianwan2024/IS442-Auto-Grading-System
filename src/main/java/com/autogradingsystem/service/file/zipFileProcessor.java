package com.autogradingsystem.service.file;

import java.io.IOException;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * ZipFileProcessor - Low-level ZIP extraction utility
 * 
 * PURPOSE:
 * - Safely extracts a single ZIP file to a destination directory
 * - Handles security (zip slip attack prevention)
 * - Cross-platform compatible (Windows, macOS, Linux)
 * 
 * CROSS-PLATFORM NOTES:
 * - Uses java.nio.file.Path instead of String paths
 * - Path.resolve() automatically uses correct separator (\ on Windows, / on Mac/Linux)
 * - Files API works identically on all platforms
 * 
 * SECURITY:
 * - Validates all ZIP entries to prevent "zip slip" attacks
 * - Zip slip: malicious ZIP with entries like "../../etc/passwd" that escape target directory
 * 
 * @author IS442 Team
 * @version 2.0 (Enhanced with security and logging)
 */
public class ZipFileProcessor {
    
    /**
     * Extracts a ZIP file to the specified destination directory.
     * 
     * WORKFLOW:
     * 1. Create destination directory if it doesn't exist
     * 2. Open ZIP file as a stream
     * 3. For each entry in the ZIP:
     *    a. Validate it's not trying to escape target directory (security)
     *    b. Create parent directories if needed
     *    c. Extract file or create directory
     * 4. Count files extracted
     * 5. Return count (for validation)
     * 
     * CROSS-PLATFORM HANDLING:
     * - ZIP entries may have different path separators depending on OS that created them
     * - Windows ZIPs: "folder\subfolder\file.txt"
     * - Mac/Linux ZIPs: "folder/subfolder/file.txt"
     * - Path.resolve() handles BOTH correctly!
     * 
     * @param zipFilePath Path to the ZIP file to extract
     * @param destDirectory Path to the destination directory
     * @return Number of files extracted (not including directories)
     * @throws IOException if extraction fails or security validation fails
     */
    public static int unzip(Path zipFilePath, Path destDirectory) throws IOException {
        
        // STEP 1: Ensure destination directory exists
        // Files.createDirectories() is safe to call even if directory already exists
        // It creates all parent directories if needed (like 'mkdir -p' on Unix)
        Files.createDirectories(destDirectory);
        
        int fileCount = 0;  // Track number of files extracted
        
        // STEP 2: Open ZIP file as a stream
        // try-with-resources ensures the stream is automatically closed even if error occurs
        // Files.newInputStream() is modern Java - works on all platforms
        try (ZipInputStream zipIn = new ZipInputStream(Files.newInputStream(zipFilePath))) {
            
            ZipEntry entry = zipIn.getNextEntry();  // Get first entry in ZIP
            
            // STEP 3: Loop through all entries in the ZIP file
            while (entry != null) {
                
                // STEP 3a: Build the full path for this entry
                // destDirectory.resolve(entry.getName()) combines paths correctly
                // Example: destDirectory = "data/extracted/student1"
                //          entry.getName() = "Q1/Q1a.java"
                //          Result on Windows: data\extracted\student1\Q1\Q1a.java
                //          Result on Mac: data/extracted/student1/Q1/Q1a.java
                Path targetPath = destDirectory.resolve(entry.getName());
                
                // STEP 3b: SECURITY CHECK - Prevent zip slip attack
                // normalize() removes ".." and "." from paths
                // startsWith() checks if the normalized path is still inside destDirectory
                // 
                // ATTACK EXAMPLE:
                //   Malicious ZIP entry: "../../etc/passwd"
                //   Without check: Would extract to /etc/passwd (BAD!)
                //   With check: Detects path escapes destDirectory, throws error
                //
                // WHY NORMALIZE?
                //   Path: "data/extracted/student1/../../../etc/passwd"
                //   Normalized: "/etc/passwd"
                //   Clearly NOT inside "data/extracted/student1"
                if (!targetPath.normalize().startsWith(destDirectory.normalize())) {
                    throw new IOException(
                        "❌ SECURITY: Zip entry attempts to escape target directory: " + entry.getName()
                    );
                }
                
                // STEP 3c: Handle directories vs files differently
                if (entry.isDirectory()) {
                    // ZIP entry is a directory - just create it
                    Files.createDirectories(targetPath);
                    
                } else {
                    // ZIP entry is a file - extract it
                    
                    // SUBSTEP 1: Ensure parent directory exists
                    // Example: For "Q1/Q1a.java", ensure "Q1/" directory exists
                    // targetPath.getParent() returns the directory part
                    Files.createDirectories(targetPath.getParent());
                    
                    // SUBSTEP 2: Extract the file
                    // Files.copy(InputStream, Path, options) is the modern way
                    // StandardCopyOption.REPLACE_EXISTING allows re-running without errors
                    // This is important: if we grade twice, second run won't crash
                    Files.copy(zipIn, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    
                    fileCount++;  // Increment counter (only for files, not directories)
                }
                
                zipIn.closeEntry();  // Finish processing this entry
                entry = zipIn.getNextEntry();  // Move to next entry (null if no more)
            }
        }
        // Stream automatically closed here by try-with-resources
        
        // STEP 4: Log success with file count
        // Using only filename for cleaner output (not full path)
        System.out.println("   📦 Extracted " + fileCount + " files from: " + zipFilePath.getFileName());
        
        return fileCount;
    }
    
    /**
     * Legacy method for backward compatibility.
     * Accepts String paths and converts them to Path objects.
     * 
     * WHY KEEP THIS?
     * - Existing code might use String paths
     * - Easier migration - don't have to change all callers at once
     * - Eventually can be deprecated
     * 
     * RECOMMENDED: New code should use the Path version above
     * 
     * @param zipFilePath String path to ZIP file
     * @param destDirectory String path to destination
     * @return Number of files extracted
     * @throws IOException if extraction fails
     */
    public static int unzip(String zipFilePath, String destDirectory) throws IOException {
        // Convert String paths to Path objects and call main method
        return unzip(Paths.get(zipFilePath), Paths.get(destDirectory));
    }
}