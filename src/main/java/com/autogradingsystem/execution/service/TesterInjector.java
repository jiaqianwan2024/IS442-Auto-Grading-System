package com.autogradingsystem.execution.service;

import com.autogradingsystem.PathConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * TesterInjector - Copies Tester Files (and their dependencies) to Student Folders
 *
 * EDGE CASES HAND * PURPOSE:
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
     * Copies the tester .java file AND any non-Java dependency files from the same
     * question folder in the template (e.g., persons.txt, students.txt) into the
     * student's question folder.
     *
     * WHY COPY DATA FILES?
     * Some testers read from files like "persons.txt" using a relative path.
     * If those files aren't in the student's folder, the tester crashes at runtime
     * even though it compiled fine.
     *
     * @param testerFile        Tester filename (e.g., "Q1aTester.java")
     * @param destinationFolder Student's question folder
     * @param questionFolder    Question folder name (e.g., "Q1") — used to locate data files
     * @throws IOException if tester cannot be found or copied
     */
    public void copyTester(String testerFile, Path destinationFolder, String questionFolder)
            throws IOException {

        // Guards
        if (testerFile == null || testerFile.isBlank())
            throw new IllegalArgumentException("testerFile must not be blank");
        if (destinationFolder == null)
            throw new IllegalArgumentException("destinationFolder must not be null");

        // Auto-create destination if it doesn't exist
        Files.createDirectories(destinationFolder);

        // Copy the tester .java file
        copyFile(testerFile, PathConfig.INPUT_TESTERS, destinationFolder);

        // Copy data files (non-Java files) from the template question folder
        if (questionFolder != null && !questionFolder.isBlank()) {
            copyDataFiles(questionFolder, destinationFolder);
        }
    }

    /**
     * Convenience overload — no data file copying (backwards compatible).
     */
    public void copyTester(String testerFile, Path destinationFolder) throws IOException {
        copyTester(testerFile, destinationFolder, null);
    }

    /**
     * Returns true if the tester file exists on filesystem or classpath.
     */
    public boolean testerExists(String testerFile) {
        if (testerFile == null || testerFile.isBlank()) return false;

        Path path = PathConfig.INPUT_TESTERS.resolve(testerFile);
        if (Files.exists(path)) return true;

        try (InputStream s = getClass().getClassLoader()
                .getResourceAsStream("testers/" + testerFile)) {
            return s != null;
        } catch (IOException e) {
            return false;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Copies a single file from sourceDir to destinationFolder.
     * Tries filesystem first, then classpath (JAR mode).
     */
    private void copyFile(String filename, Path sourceDir, Path destinationFolder)
            throws IOException {

        Path source = sourceDir.resolve(filename);
        Path dest   = destinationFolder.resolve(filename);

        if (Files.exists(source)) {
            Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
            return;
        }

        // Fallback: classpath (JAR deployment)
        String resourcePath = "testers/" + filename;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException(
                    "Tester not found: " + filename + "\n"
                    + "  Filesystem: " + source + "\n"
                    + "  Classpath:  " + resourcePath + "\n"
                    + "Ensure the file exists in resources/input/testers/");
            }
            Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Copies non-Java files (e.g., .txt, .csv) from the template question folder
     * into the student's question folder.
     *
     * EDGE CASE: testers that do FileReader("persons.txt") need the file co-located.
     * If the template has Q2/persons.txt, we copy it to the student's Q2/ folder.
     */
    private void copyDataFiles(String questionFolder, Path destinationFolder) {
        // Template question folder path
        Path templateQuestionDir = PathConfig.INPUT_TEMPLATE.resolve(questionFolder);
        if (!Files.exists(templateQuestionDir)) return;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(templateQuestionDir)) {
            for (Path file : stream) {
                String name = file.getFileName().toString();
                // Skip .java and .class — only copy data/resource files
                if (name.endsWith(".java") || name.endsWith(".class")) continue;
                if (Files.isDirectory(file)) continue;

                Path dest = destinationFolder.resolve(name);
                try {
                    Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    // Non-fatal — log and continue
                    System.out.println("[TesterInjector] ⚠️  Could not copy data file "
                            + name + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            // Template folder unreadable — non-fatal, student may not need data files
            System.out.println("[TesterInjector] ⚠️  Could not read template folder for "
                    + questionFolder + ": " + e.getMessage());
        }
    }
}