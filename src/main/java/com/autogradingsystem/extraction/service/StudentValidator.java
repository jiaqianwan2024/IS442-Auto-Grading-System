package com.autogradingsystem.extraction.service;

import com.autogradingsystem.extraction.model.ValidationResult;
import com.autogradingsystem.extraction.model.ValidationResult.Status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.MalformedInputException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * StudentValidator - 3-Layer Student Identification System
 *
 * Layer 1: ZIP filename pattern matching
 * Layer 2: Folder name scanning inside ZIP
 * Layer 3: Java header scanning for Name / Email / ID metadata
 */
public class StudentValidator {

    private static final int HEADER_SCAN_LINES = 12;

    private static final Pattern ZIP_NAME_PATTERN =
            Pattern.compile("(?i)(?:\\d{4}-\\d{4}-)?(.+?)\\.zip$");
    private static final Pattern NAME_PATTERN =
            Pattern.compile("(?i)^\\s*(?:/\\*+\\s*)?(?:\\*+\\s*)?Name\\s*:\\s*(.+?)\\s*(?:\\*/)?\\s*$");
    private static final Pattern IDENTIFIER_PATTERN =
            Pattern.compile("(?i)^\\s*(?:/\\*+\\s*)?(?:\\*+\\s*)?(?:Email\\s*ID|Email|ID)\\s*:\\s*(\\S+)\\s*(?:\\*/)?\\s*$");

    /**
     * Validates student using layered detection and extracts if valid.
     */
    public ValidationResult validate3Layer(
            Path studentZip,
            Path destination,
            ScoreSheetReader scoreReader) throws IOException {

        String zipFilename = studentZip.getFileName().toString();

        String usernameFromFilename = extractUsernameFromFilename(zipFilename);
        if (usernameFromFilename != null && scoreReader.isValid(usernameFromFilename)) {
            Path extractPath = destination.resolve(usernameFromFilename);
            ZipFileProcessor.unzip(studentZip, extractPath);
            return new ValidationResult(zipFilename, Status.MATCHED, usernameFromFilename);
        }

        String usernameFromFolder = extractUsernameFromFolder(studentZip, scoreReader);
        if (usernameFromFolder != null) {
            Path extractPath = destination.resolve(usernameFromFolder);
            ZipFileProcessor.unzip(studentZip, extractPath);
            return new ValidationResult(zipFilename, Status.RECOVERED_FOLDER, usernameFromFolder);
        }

        String usernameFromHeader = extractUsernameFromHeaders(studentZip, scoreReader);
        if (usernameFromHeader != null) {
            Path extractPath = destination.resolve(usernameFromHeader);
            ZipFileProcessor.unzip(studentZip, extractPath);
            return new ValidationResult(zipFilename, Status.RECOVERED_COMMENT, usernameFromHeader);
        }

        String rawName = zipFilename.replace(".zip", "").replaceFirst("^(\\d{4}-)+", "");
        Path extractPath = destination.resolve(rawName);
        ZipFileProcessor.unzip(studentZip, extractPath);
        return new ValidationResult(zipFilename, Status.UNRECOGNIZED, rawName);
    }

    private String extractUsernameFromFilename(String filename) {
        Matcher matcher = ZIP_NAME_PATTERN.matcher(filename);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String extractUsernameFromFolder(Path studentZip, ScoreSheetReader scoreReader)
            throws IOException {

        Path tempDir = Files.createTempDirectory("student-check");
        try {
            ZipFileProcessor.unzip(studentZip, tempDir);
            try (var stream = Files.walk(tempDir)) {
                Path[] folders = stream
                        .filter(Files::isDirectory)
                        .filter(path -> !path.equals(tempDir))
                        .toArray(Path[]::new);

                for (Path folder : folders) {
                    String folderName = folder.getFileName().toString().trim();
                    if (folderName.isEmpty()) {
                        continue;
                    }

                    if (scoreReader.isValid(folderName)) {
                        return folderName;
                    }

                    String strippedName = folderName.replaceFirst("^(\\d{4}-)+", "");
                    if (!strippedName.equals(folderName) && scoreReader.isValid(strippedName)) {
                        return strippedName;
                    }
                }
            }
            return null;
        } finally {
            deleteDirectory(tempDir);
        }
    }

    /**
     * Layer 3:
     * Scan the first few lines of Java files for a header comment like:
     *   Name: Teo Chee
     *   Email ID: chee.teo.2022
     * or:
     *   Email: chee.teo.2022@computing.smu.edu.sg
     *   ID: chee.teo.2022
     *
     * We do not rely on @author tags because students usually do not use them.
     */
    private String extractUsernameFromHeaders(Path studentZip, ScoreSheetReader scoreReader)
            throws IOException {

        Path tempDir = Files.createTempDirectory("header-scan");
        try {
            ZipFileProcessor.unzip(studentZip, tempDir);

            try (var stream = Files.walk(tempDir)) {
                Path[] javaFiles = stream
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".java"))
                        .toArray(Path[]::new);

                for (Path javaFile : javaFiles) {
                    List<String> lines = readLinesLenient(javaFile);
                    int maxLines = Math.min(HEADER_SCAN_LINES, lines.size());

                    String foundName = null;
                    String foundIdentifier = null;

                    for (int i = 0; i < maxLines; i++) {
                        String line = lines.get(i);

                        Matcher nameMatcher = NAME_PATTERN.matcher(line);
                        if (foundName == null && nameMatcher.find()) {
                            foundName = nameMatcher.group(1).trim();
                        }

                        Matcher idMatcher = IDENTIFIER_PATTERN.matcher(line);
                        if (foundIdentifier == null && idMatcher.find()) {
                            foundIdentifier = idMatcher.group(1).trim();
                        }
                    }

                    if (foundName != null && foundIdentifier != null) {
                        String resolved = scoreReader.resolveUsernameFromIdentifier(foundIdentifier);
                        if (resolved != null) {
                            return resolved;
                        }
                    }
                }
            }

            return null;
        } finally {
            deleteDirectory(tempDir);
        }
    }

    private List<String> readLinesLenient(Path javaFile) throws IOException {
        try {
            return Files.readAllLines(javaFile, StandardCharsets.UTF_8);
        } catch (MalformedInputException e) {
            return Files.readAllLines(javaFile, StandardCharsets.ISO_8859_1);
        }
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) return;

        Files.walk(directory)
                .sorted(java.util.Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException ignored) {
                    }
                });
    }
}
