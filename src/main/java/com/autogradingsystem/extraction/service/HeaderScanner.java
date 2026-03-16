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
 */
public class HeaderScanner {

    private static final Pattern NAME_PATTERN =
        Pattern.compile("\\*\\s*Name:\\s*(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("\\*\\s*Email\\s*ID:\\s*(\\S+)", Pattern.CASE_INSENSITIVE);

    public static class ScanResult {
        // Email found in any header (used for identity resolution)
        public String resolvedEmail = null;
        // Files that are missing the header: e.g. ["Q1a.java", "Q2b.java"]
        public List<String> missingHeaders = new ArrayList<>();
    }

    /**
     * Scans only the main question files (derived from grading tasks).
     *
     * @param studentRoot  root path of the extracted student folder
     * @param tasks        grading tasks (used to derive expected filenames)
     * @return ScanResult with resolved email and list of files missing headers
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
                    // Use first found email for identity resolution
                    if (result.resolvedEmail == null) {
                        result.resolvedEmail = email.trim().toLowerCase();
                    }
                }
            } catch (IOException e) {
                result.missingHeaders.add(expectedFile);
            }
        }

        return result;
    }

    private String extractEmail(String content) {
        Matcher m = EMAIL_PATTERN.matcher(content);
        // Header must appear in first 20 lines
        String[] lines = content.split("\n", 25);
        StringBuilder top = new StringBuilder();
        for (int i = 0; i < Math.min(20, lines.length); i++) top.append(lines[i]).append("\n");
        Matcher topMatcher = EMAIL_PATTERN.matcher(top.toString());
        return topMatcher.find() ? topMatcher.group(1).trim() : null;
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