package com.autogradingsystem.model;

import com.autogradingsystem.PathConfig;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Student - Represents a Student and Their Submission Location
 *
 * PURPOSE:
 * - Encapsulates student information and folder location
 * - Provides convenient path building methods
 * - Shared across all services
 *
 * CHANGES IN v3.2:
 * - id is now mutable (identity may be resolved from header email)
 * - Added folderRenamed, missingHeaderFiles, anomaly, rawFolderName for header scanning
 * - Added headerMismatch, headerClaimedUsername for ZIP vs header identity conflict detection
 *
 * @author IS442 Team
 * @version 3.2
 */
public class Student {

    // ── Fields ────────────────────────────────────────────────────────────────

    private String id;                                         // mutable — may be resolved from header email
    private final Path rootPath;

    // Identity / header scan flags (set by ExecutionController.loadStudents)
    private boolean folderRenamed        = true;
    private List<String> missingHeaderFiles = new ArrayList<>();
    private boolean anomaly              = false;
    private String rawFolderName         = null;

    // Header mismatch: ZIP name resolves to one student but header claims another
    private boolean headerMismatch          = false;
    private String headerClaimedUsername    = null;

    // ── Constructors ──────────────────────────────────────────────────────────

    /**
     * Primary constructor.
     * @param id       Student username (e.g. "ping.lee.2023")
     * @param rootPath Path to student's root extracted folder
     */
    public Student(String id, Path rootPath) {
        this.id = id;
        this.rootPath = rootPath;
    }

    /**
     * Convenience constructor — accepts String path.
     * @param id         Student username
     * @param folderPath String path to student's root folder
     */
    public Student(String id, String folderPath) {
        this.id = id;
        this.rootPath = Paths.get(folderPath);
    }

    // ── Core getters ──────────────────────────────────────────────────────────

    public String getId()          { return id; }
    public String getUsername()    { return id; }   // alias for clarity
    public Path   getRootPath()    { return rootPath; }

    /**
     * Builds path to a specific question folder for this student.
     * @param questionFolder e.g. "Q1"
     * @return Path to student's question folder
     */
    public Path getQuestionPath(String questionFolder) {
        return PathConfig.getStudentQuestionFolder(this.id, questionFolder);
    }

    // ── Identity / header flags ───────────────────────────────────────────────

    /** Set by identity resolution — false if ZIP was not renamed to a valid username. */
    public boolean isFolderRenamed()                          { return folderRenamed; }
    public void    setFolderRenamed(boolean folderRenamed)    { this.folderRenamed = folderRenamed; }

    /** List of question filenames (e.g. "Q1a.java") whose headers are missing. */
    public List<String> getMissingHeaderFiles()                             { return missingHeaderFiles; }
    public void         setMissingHeaderFiles(List<String> files)          { this.missingHeaderFiles = files; }

    /** True if submission is unidentifiable (no folder rename + no valid headers). */
    public boolean isAnomaly()                    { return anomaly; }
    public void    setAnomaly(boolean anomaly)    { this.anomaly = anomaly; }

    /** Original extracted folder name before any identity resolution. */
    public String getRawFolderName()                        { return rawFolderName; }
    public void   setRawFolderName(String rawFolderName)   { this.rawFolderName = rawFolderName; }

    /**
     * True when the ZIP name resolves to one valid student (e.g. hongyao.2024)
     * but the file headers claim a different student (e.g. hongyee.2024).
     * Grading proceeds under the ZIP identity — this flag triggers a remark only.
     */
    public boolean isHeaderMismatch()                           { return headerMismatch; }
    public void    setHeaderMismatch(boolean headerMismatch)    { this.headerMismatch = headerMismatch; }

    /**
     * The username claimed by the file headers when a mismatch is detected.
     * e.g. if hongyao.2024's files all say hongyee.2024, this returns "hongyee.2024".
     */
    public String getHeaderClaimedUsername()                            { return headerClaimedUsername; }
    public void   setHeaderClaimedUsername(String headerClaimedUsername) { this.headerClaimedUsername = headerClaimedUsername; }

    /** Allows identity to be updated after header-based resolution. */
    public void setId(String id) { this.id = id; }


private String headerMismatchFile = null;
public String getHeaderMismatchFile() { return headerMismatchFile; }
public void setHeaderMismatchFile(String f) { this.headerMismatchFile = f; }

    // ── Object methods ────────────────────────────────────────────────────────

    @Override
    public String toString() {
        return String.format("Student{id='%s', path=%s}", id, rootPath);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        return id.equals(((Student) obj).id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}