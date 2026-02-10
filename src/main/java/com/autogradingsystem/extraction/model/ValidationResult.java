package com.autogradingsystem.extraction.model;

/**
 * ValidationResult - Represents the outcome of student validation
 * 
 * PURPOSE:
 * - Encapsulates the result of 3-layer student identification
 * - Communicates how student was identified (or if identification failed)
 * - Stores the original filename and resolved student ID
 * 
 * DESIGN:
 * - Immutable (all fields final) for thread safety
 * - Clear status enum for different identification methods
 * - Used by StudentValidator and consumed by ExtractionController
 * 
 * CHANGES FROM v3.0:
 * - Extracted from StudentValidator as standalone model class
 * - Follows Spring Boot model package convention
 * - Added comprehensive JavaDoc
 * 
 * @author IS442 Team
 * @version 4.0 (Spring Boot Microservices Structure)
 */
public class ValidationResult {
    
    /**
     * Status enum - Indicates how student was identified
     * 
     * MATCHED: Layer 1 succeeded (filename matched)
     * RECOVERED_FOLDER: Layer 2 succeeded (folder name matched)
     * RECOVERED_COMMENT: Layer 3 succeeded (Java comment matched)
     * UNRECOGNIZED: All layers failed (could not identify student)
     */
    public enum Status {
        /**
         * Layer 1: Filename matched expected pattern
         * Most reliable - student submitted correctly
         */
        MATCHED,
        
        /**
         * Layer 2: Folder name inside ZIP matched
         * Student made filename mistake but folder is correct
         */
        RECOVERED_FOLDER,
        
        /**
         * Layer 3: @author comment in Java file matched
         * Last resort - student made multiple mistakes
         */
        RECOVERED_COMMENT,
        
        /**
         * All 3 layers failed - could not identify student
         * Either not in official list or submission too malformed
         */
        UNRECOGNIZED
    }
    
    // ================================================================
    // FIELDS
    // ================================================================
    
    /**
     * Original ZIP filename submitted by student
     * Example: "my-submission.zip" or "2023-2024-ping.lee.2023.zip"
     */
    private final String originalFilename;
    
    /**
     * Status indicating which detection layer succeeded
     */
    private final Status status;
    
    /**
     * Resolved student username (cleaned, validated)
     * Example: "ping.lee.2023"
     * null if status is UNRECOGNIZED
     */
    private final String resolvedId;
    
    // ================================================================
    // CONSTRUCTOR
    // ================================================================
    
    /**
     * Creates a ValidationResult
     * 
     * @param originalFilename Original ZIP filename
     * @param status How student was identified
     * @param resolvedId Resolved username (null if UNRECOGNIZED)
     */
    public ValidationResult(String originalFilename, Status status, String resolvedId) {
        this.originalFilename = originalFilename;
        this.status = status;
        this.resolvedId = resolvedId;
    }
    
    // ================================================================
    // GETTERS
    // ================================================================
    
    /**
     * Gets the original ZIP filename
     * 
     * @return Original filename as submitted by student
     */
    public String getOriginalFilename() {
        return originalFilename;
    }
    
    /**
     * Gets the validation status
     * 
     * @return Status enum indicating how student was identified
     */
    public Status getStatus() {
        return status;
    }
    
    /**
     * Gets the resolved student ID
     * 
     * @return Validated username, or null if UNRECOGNIZED
     */
    public String getResolvedId() {
        return resolvedId;
    }
    
    // ================================================================
    // HELPER METHODS
    // ================================================================
    
    /**
     * Checks if student was successfully identified
     * 
     * CONVENIENCE METHOD:
     * Instead of: if (result.getStatus() != Status.UNRECOGNIZED)
     * Use: if (result.isIdentified())
     * 
     * @return true if student was identified (any layer succeeded), false otherwise
     */
    public boolean isIdentified() {
        return status != Status.UNRECOGNIZED;
    }
    
    /**
     * Checks if student submitted with correct filename
     * 
     * @return true if Layer 1 matched, false otherwise
     */
    public boolean isExactMatch() {
        return status == Status.MATCHED;
    }
    
    /**
     * Checks if student was recovered via fallback methods
     * 
     * @return true if Layer 2 or 3 succeeded, false otherwise
     */
    public boolean wasRecovered() {
        return status == Status.RECOVERED_FOLDER || status == Status.RECOVERED_COMMENT;
    }
    
    // ================================================================
    // OBJECT METHODS
    // ================================================================
    
    /**
     * String representation for debugging
     * 
     * @return Human-readable representation
     */
    @Override
    public String toString() {
        return String.format(
            "ValidationResult{file='%s', status=%s, id='%s'}",
            originalFilename,
            status,
            resolvedId != null ? resolvedId : "NONE"
        );
    }
    
    /**
     * Checks equality based on all fields
     * 
     * @param obj Object to compare with
     * @return true if equal, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        ValidationResult other = (ValidationResult) obj;
        
        return originalFilename.equals(other.originalFilename) &&
               status == other.status &&
               (resolvedId != null ? resolvedId.equals(other.resolvedId) : other.resolvedId == null);
    }
    
    /**
     * Generates hash code based on all fields
     * 
     * @return Hash code
     */
    @Override
    public int hashCode() {
        int result = originalFilename.hashCode();
        result = 31 * result + status.hashCode();
        result = 31 * result + (resolvedId != null ? resolvedId.hashCode() : 0);
        return result;
    }
}