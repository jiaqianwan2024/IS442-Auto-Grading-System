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
 */
public class TesterInjector {

    // ================================================================
    // PATH FIELDS — null means "use global PathConfig" (single-assessment)
    // ================================================================

    private final Path inputTesters;
    private final Path inputTemplate;

    // ================================================================
    // CONSTRUCTORS
    // ================================================================

    /**
     * Default constructor — uses global PathConfig static paths.
     * Called by ExecutionController for the standard single-assessment flow.
     */
    public TesterInjector() {
        this.inputTesters = null;
        this.inputTemplate = null;
    }

    /**
     * Path-aware constructor for multi-assessment support.
     * Called by ExecutionController when constructed with per-assessment paths.
     *
     * @param inputTesters  Path to this assessment's testers directory
     * @param inputTemplate Path to this assessment's template directory
     */
    public TesterInjector(Path inputTesters, Path inputTemplate) {
        this.inputTesters = inputTesters;
        this.inputTemplate = inputTemplate;
    }

    // ================================================================
    // PATH RESOLUTION
    // ================================================================

    private Path resolveInputTesters() {
        return inputTesters != null ? inputTesters : PathConfig.INPUT_TESTERS;
    }

    private Path resolveInputTemplate() {
        return inputTemplate != null ? inputTemplate : PathConfig.INPUT_TEMPLATE;
    }

    // ================================================================
    // PUBLIC API
    // ================================================================

    /**
     * Copies the tester .java file AND any non-Java dependency files from the same
     * question folder in the template into the student's question folder.
     *
     * @param testerFile        Tester filename (e.g., "Q1aTester.java")
     * @param destinationFolder Student's question folder
     * @param questionFolder    Question folder name (e.g., "Q1")
     * @throws IOException if tester cannot be found or copied
     */
    public void copyTester(String testerFile, Path destinationFolder, String questionFolder)
            throws IOException {

        if (testerFile == null || testerFile.isBlank())
            throw new IllegalArgumentException("testerFile must not be blank");
        if (destinationFolder == null)
            throw new IllegalArgumentException("destinationFolder must not be null");

        Files.createDirectories(destinationFolder);

        // Copy the tester .java file from the resolved testers path
        copyFile(testerFile, resolveInputTesters(), destinationFolder);

        // Copy data files from the resolved template question folder
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

        Path path = resolveInputTesters().resolve(testerFile);
        if (Files.exists(path)) return true;

        try (InputStream s = getClass().getClassLoader()
                .getResourceAsStream("testers/" + testerFile)) {
            return s != null;
        } catch (IOException e) {
            return false;
        }
    }

    // ================================================================
    // PRIVATE HELPERS
    // ================================================================

    private void copyFile(String filename, Path sourceDir, Path destinationFolder)
            throws IOException {

        Path source = sourceDir.resolve(filename);
        String content;

        if (Files.exists(source)) {
            content = Files.readString(source);
        } else {
            String resourcePath = "testers/" + filename;
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                if (in == null) throw new IOException("Tester not found: " + filename);
                content = new String(in.readAllBytes());
            }
        }

        // THE NINJA MOVE: Inject partial score tracking
        String injectedContent = content.replaceAll(
            "(?i)(score\\s*\\+=\\s*[^;]+;)",
            "$1 System.out.println(\"PARTIAL_SCORE:\" + score); System.out.flush();"
        );

        Files.writeString(destinationFolder.resolve(filename), injectedContent);
    }

    private void copyDataFiles(String questionFolder, Path destinationFolder) {
        Path templateQuestionDir = resolveInputTemplate().resolve(questionFolder);
        if (!Files.exists(templateQuestionDir)) return;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(templateQuestionDir)) {
            for (Path file : stream) {
                String name = file.getFileName().toString();
                if (name.endsWith(".java")) continue;
                if (Files.isDirectory(file)) continue;
                // Never overwrite student's own Q4 scripts with template stubs
                if (name.equalsIgnoreCase("compile.bat") || name.equalsIgnoreCase("compile.sh")
                        || name.equalsIgnoreCase("run.bat") || name.equalsIgnoreCase("run.sh")) continue;

                Path dest = destinationFolder.resolve(name);
                try {
                    Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    System.out.println("[TesterInjector] ⚠️  Could not copy data file "
                            + name + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.out.println("[TesterInjector] ⚠️  Could not read template folder for "
                    + questionFolder + ": " + e.getMessage());
        }
    }
}