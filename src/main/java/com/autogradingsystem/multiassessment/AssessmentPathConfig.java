package com.autogradingsystem.multiassessment;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * AssessmentPathConfig - Per-Assessment Isolated Path Configuration
 *
 * PURPOSE:
 *   Mirrors PathConfig but scoped to a single named assessment so that
 *   multiple assessments can run concurrently without touching each other's
 *   files. Each assessment gets its own subdirectory under:
 *
 *     resources/assessments/<assessmentName>/
 *       input/
 *         submissions/   ← student-submission.zip
 *         template/      ← RenameToYourUsername.zip
 *         testers/       ← Q1aTester.java, Q2aTester.java, ...
 *       config/
 *         scoresheet.csv
 *       output/
 *         extracted/     ← unzipped student folders
 *         reports/       ← CSV + Excel exports
 *
 * DESIGN:
 *   - Instance-based (not static) so each assessment has its own paths.
 *   - Drop-in replacement for PathConfig in all controllers — controllers
 *     accept an AssessmentPathConfig instead of reading PathConfig statics.
 *   - The existing single-assessment flow is unchanged; GradingService still
 *     uses PathConfig. Only the multi-assessment path goes through here.
 *
 */
public class AssessmentPathConfig {

    // ================================================================
    // FIELDS
    // ================================================================

    /** Human-readable name of this assessment, e.g. "Midterm", "Lab-Test-1" */
    private final String assessmentName;

    /** Root directory for this assessment: resources/assessments/<name>/ */
    private final Path assessmentRoot;

    // ── Input paths ──────────────────────────────────────────────────

    public final Path INPUT_BASE;
    public final Path INPUT_SUBMISSIONS;
    public final Path INPUT_TEMPLATE;
    public final Path INPUT_TESTERS;
    public final Path CSV_SCORESHEET;
    public final Path INPUT_EXAM; 

    // ── Output paths ─────────────────────────────────────────────────

    public final Path OUTPUT_BASE;
    public final Path OUTPUT_EXTRACTED;
    public final Path OUTPUT_REPORTS;

    // ================================================================
    // CONSTRUCTOR
    // ================================================================

    /**
     * Creates a fully isolated path config for one assessment.
     *
     * @param assessmentName Sanitised name used as the directory name.
     *                       Must be a valid directory name — spaces and
     *                       special characters are replaced by the factory
     *                       method {@link #forName(String)}.
     */
    public AssessmentPathConfig(String assessmentName) {
        this.assessmentName = assessmentName;
        this.assessmentRoot = Paths.get("resources", "assessments", assessmentName);

        this.INPUT_BASE        = assessmentRoot.resolve("input");
        this.INPUT_SUBMISSIONS = INPUT_BASE.resolve("submissions");
        this.INPUT_TEMPLATE    = INPUT_BASE.resolve("template");
        this.INPUT_TESTERS     = INPUT_BASE.resolve("testers");
        this.INPUT_EXAM        = INPUT_BASE.resolve("exam");
        this.CSV_SCORESHEET    = assessmentRoot.resolve("config").resolve("scoresheet.csv");

        this.OUTPUT_BASE      = assessmentRoot.resolve("output");
        this.OUTPUT_EXTRACTED = OUTPUT_BASE.resolve("extracted");
        this.OUTPUT_REPORTS   = OUTPUT_BASE.resolve("reports");
    }

    // ================================================================
    // FACTORY
    // ================================================================

    /**
     * Creates an AssessmentPathConfig from a raw assessment name, sanitising
     * the name so it is safe to use as a directory name.
     *
     * Sanitisation rules:
     *   - Trim whitespace
     *   - Replace spaces and slashes with hyphens
     *   - Strip characters that are not alphanumeric, hyphens, dots, or underscores
     *   - Convert to lowercase
     *
     * @param rawName Raw assessment name from the upload form, e.g. "Lab Test 1"
     * @return AssessmentPathConfig with a sanitised directory name
     */
    public static AssessmentPathConfig forName(String rawName) {
        String sanitised = rawName
                .trim()
                .toLowerCase()
                .replaceAll("[\\s/\\\\]+", "-")
                .replaceAll("[^a-z0-9._-]", "")
                .replaceAll("-{2,}", "-");

        if (sanitised.isEmpty()) {
            sanitised = "assessment-" + System.currentTimeMillis();
        }

        return new AssessmentPathConfig(sanitised);
    }

    // ================================================================
    // LIFECYCLE
    // ================================================================

    /**
     * Creates all required input and output directories.
     * Safe to call multiple times — uses mkdirs() semantics.
     */
    public void ensureDirectories() {
        INPUT_SUBMISSIONS.toFile().mkdirs();
        INPUT_TEMPLATE.toFile().mkdirs();
        INPUT_TESTERS.toFile().mkdirs();
        INPUT_EXAM.toFile().mkdirs();  
        CSV_SCORESHEET.getParent().toFile().mkdirs();
        OUTPUT_EXTRACTED.toFile().mkdirs();
        OUTPUT_REPORTS.toFile().mkdirs();
    }

    /**
     * Validates that all required input files and directories are present.
     *
     * @return true if all required inputs exist, false otherwise
     */
    public boolean validateInputPaths() {
        boolean valid = true;

        if (!CSV_SCORESHEET.toFile().exists()) {
            System.err.println("[" + assessmentName + "] ❌ Missing scoresheet: " + CSV_SCORESHEET);
            valid = false;
        }
        if (!INPUT_SUBMISSIONS.toFile().exists()) {
            System.err.println("[" + assessmentName + "] ❌ Missing submissions dir: " + INPUT_SUBMISSIONS);
            valid = false;
        }
        if (!INPUT_TEMPLATE.toFile().exists()) {
            System.err.println("[" + assessmentName + "] ❌ Missing template dir: " + INPUT_TEMPLATE);
            valid = false;
        }
        if (!INPUT_TESTERS.toFile().exists()) {
            System.err.println("[" + assessmentName + "] ❌ Missing testers dir: " + INPUT_TESTERS);
            valid = false;
        }

        return valid;
    }

    // ================================================================
    // HELPER METHODS  (mirrors PathConfig helper API)
    // ================================================================

    /**
     * Builds the path to a specific student's extracted root folder.
     *
     * @param username Student username, e.g. "ping.lee.2023"
     * @return Path to student's extracted folder
     */
    public Path getStudentFolder(String username) {
        return OUTPUT_EXTRACTED.resolve(username);
    }

    /**
     * Builds the path to a specific student's question folder.
     *
     * @param username       Student username
     * @param questionFolder Question folder name, e.g. "Q1"
     * @return Path to student's question folder
     */
    public Path getStudentQuestionFolder(String username, String questionFolder) {
        return OUTPUT_EXTRACTED.resolve(username).resolve(questionFolder);
    }

    // ================================================================
    // GETTERS
    // ================================================================

    /** @return The sanitised assessment name used as the directory name */
    public String getAssessmentName() {
        return assessmentName;
    }

    /**
     * Converts a sanitised assessment name to a display title safe for use in
     * filenames and report headers.
     *
     * Examples:
     *   "lab-test-1"  → "Lab-Test-1"
     *   "midterm"     → "Midterm"
     *   "assessment1" → "Assessment1"
     *
     * @param sanitisedName as returned by {@link #getAssessmentName()}
     * @return title-cased, hyphen-joined display name
     */
    public static String toDisplayTitle(String sanitisedName) {
        if (sanitisedName == null || sanitisedName.isBlank()) return "Assessment";
        String[] parts = sanitisedName.split("-", -1);
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) sb.append('-');
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }

    /** @return The root directory for this assessment */
    public Path getAssessmentRoot() {
        return assessmentRoot;
    }

    @Override
    public String toString() {
        return "AssessmentPathConfig{name='" + assessmentName + "', root=" + assessmentRoot + "}";
    }
}
