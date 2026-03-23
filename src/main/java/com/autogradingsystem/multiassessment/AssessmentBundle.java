package com.autogradingsystem.multiassessment;

import java.nio.file.Path;

/**
 * AssessmentBundle - One Assessment's Complete Set of Input Files
 *
 * PURPOSE:
 *   Represents everything needed to grade one assessment:
 *     - A human-readable name (e.g. "Midterm", "Lab Test 1")
 *     - Paths to each of the 4 required input files after they have been
 *       saved to the assessment's isolated directory by
 *       MultiAssessmentController
 *
 * LIFECYCLE:
 *   1. MultiAssessmentController receives a multipart upload with N bundles.
 *   2. For each bundle it saves files to AssessmentPathConfig directories
 *      and constructs an AssessmentBundle.
 *   3. AssessmentOrchestrator receives the list of bundles and runs the
 *      full pipeline for each one concurrently.
 *
 * IMMUTABILITY:
 *   All fields are final after construction. The bundle is a pure value
 *   object — nothing modifies it after creation.
 *
 */
public class AssessmentBundle {

    // ================================================================
    // FIELDS
    // ================================================================

    /**
     * Human-readable name provided by the professor, e.g. "Midterm 2526".
     * Used in reports and log prefixes.
     */
    private final String assessmentName;

    /**
     * Isolated path configuration for this assessment.
     * All 4 input files and all output files live under pathConfig.assessmentRoot.
     */
    private final AssessmentPathConfig pathConfig;

    // ================================================================
    // CONSTRUCTOR
    // ================================================================

    /**
     * Creates a bundle for a single assessment.
     *
     * The caller (MultiAssessmentController) is responsible for ensuring
     * that all 4 input files have already been written to the paths
     * described by pathConfig before handing the bundle to the orchestrator.
     *
     * @param assessmentName Human-readable name of this assessment
     * @param pathConfig     Isolated path configuration for this assessment
     */
    public AssessmentBundle(String assessmentName, AssessmentPathConfig pathConfig) {
        this.assessmentName = assessmentName;
        this.pathConfig     = pathConfig;
    }

    // ================================================================
    // CONVENIENCE FACTORY
    // ================================================================

    /**
     * Creates a bundle from just a raw name.
     * The AssessmentPathConfig is derived automatically via
     * {@link AssessmentPathConfig#forName(String)}.
     *
     * @param rawName Raw assessment name from the upload form
     * @return AssessmentBundle ready for use
     */
    public static AssessmentBundle of(String rawName) {
        AssessmentPathConfig config = AssessmentPathConfig.forName(rawName);
        return new AssessmentBundle(rawName, config);
    }

    // ================================================================
    // GETTERS
    // ================================================================

    /**
     * @return Human-readable name as provided by the professor
     */
    public String getAssessmentName() {
        return assessmentName;
    }

    /**
     * @return Isolated path configuration for this assessment
     */
    public AssessmentPathConfig getPathConfig() {
        return pathConfig;
    }

    /**
     * Convenience: returns the sanitised directory name derived from
     * the assessment name. Useful for log prefixes.
     *
     * @return Sanitised directory name, e.g. "midterm-2526"
     */
    public String getSanitisedName() {
        return pathConfig.getAssessmentName();
    }

    // ================================================================
    // VALIDATION
    // ================================================================

    /**
     * Checks whether all 4 required input files are present on disk.
     * Delegates to {@link AssessmentPathConfig#validateInputPaths()}.
     *
     * @return true if all inputs exist, false otherwise
     */
    public boolean isReady() {
        return pathConfig.validateInputPaths();
    }

    @Override
    public String toString() {
        return "AssessmentBundle{name='" + assessmentName
                + "', sanitised='" + getSanitisedName() + "'}";
    }
}
