package com.autogradingsystem.extraction.service;

import com.autogradingsystem.model.GradingTask;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * HeaderScanner
 *
 * Scans student submission files for the required header comment:
 *   /*
 *    * Name: Ping Lee
 *    * Email ID: ping.lee.2023@computing.smu.edu.sg
 *    *\/
 *
 * PURPOSE:
 * - Extract identity (name + email) from file headers
 * - Detect missing headers per question file
 * - Support identity resolution when zip was not renamed
 * - Detect header mismatch when zip identity differs from header identity
 */
public class HeaderScanner {

    private static final Pattern NAME_PATTERN =
        Pattern.compile("\\*\\s*Name:\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("\\*\\s*Email\\s*ID:\\s*(\\S+)", Pattern.CASE_INSENSITIVE);

    public static class ScanResult {
        // Email found in any header (used for identity resolution + mismatch detection)
        public String resolvedEmail = null;
        // Name found in any header (used for mismatch detection display)
        public String resolvedName = null;
        public String resolvedFromFile = null;
        // Files that are missing the header: e.g. ["Q1a.java", "Q2b.java"]
        public List<String> missingHeaders = new ArrayList<>();
    }

    /**
     * Scans only the main question files (derived from grading tasks).
     *
     * @param studentRoot  root path of the extracted student folder
     * @param tasks        grading tasks (used to derive expected filenames)
     * @return ScanResult with resolved email, resolved name, and list of files missing headers
     */
    public ScanResult scan(Path studentRoot, List<GradingTask> tasks) {
        ScanResult result = new ScanResult();

        for (GradingTask task : tasks) {
            String expectedFile = task.getStudentFile();
            // Only scan .java files (skip script tasks)
            if (!expectedFile.endsWith(".java")) continue;

            Path javaFile = findFileRecursive(studentRoot, expectedFile);
            if (javaFile == null || !Files.exists(javaFile)) {
                // File doesn't exist — FILE_NOT_FOUND will be caught by grading, skip header check
                continue;
            }

            try {
                String content = Files.readString(javaFile);
                String email = extractEmail(content);

                if (email == null) {
                    result.missingHeaders.add(expectedFile);
                } else {
                    // Use first found email + name for identity resolution
                    if (result.resolvedEmail == null) {
                        result.resolvedEmail = email.trim().toLowerCase();
                        result.resolvedName  = extractName(content); // may be null if name line missing
                        result.resolvedFromFile = expectedFile;
                    }
                }
            } catch (IOException e) {
                result.missingHeaders.add(expectedFile);
            }
        }

        return result;
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private String extractEmail(String content) {
        String top = firstNLines(content, 20);
        Matcher m = EMAIL_PATTERN.matcher(top);
        return m.find() ? m.group(1).trim() : null;
    }

    private String extractName(String content) {
        String top = firstNLines(content, 20);
        Matcher m = NAME_PATTERN.matcher(top);
        return m.find() ? m.group(1).trim() : null;
    }

    /**
     * Returns the first n lines of content joined back as a single string.
     * Used to restrict header scanning to the top of each file.
     */
    private String firstNLines(String content, int n) {
        String[] lines = content.split("\n", n + 1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(n, lines.length); i++) {
            sb.append(lines[i]).append("\n");
        }
        return sb.toString();
    }

    private Path findFileRecursive(Path root, String filename) {
        try (var walk = Files.walk(root)) {
            return walk
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().equalsIgnoreCase(filename))
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }
}