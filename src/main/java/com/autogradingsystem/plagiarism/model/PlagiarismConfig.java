package com.autogradingsystem.plagiarism.model;

/**
 * PlagiarismConfig
 *
 * Holds all tuneable thresholds and feature-flags for the plagiarism detector.
 * Constructed once by PlagiarismController and passed down to services.
 *
 * Defaults are deliberately conservative — they flag pairs for human review,
 * not for automatic punishment.
 */
public class PlagiarismConfig {

    // ── Defaults ──────────────────────────────────────────────────────────────

    /** Pairs with combined similarity >= this value are flagged. */
    public static final double DEFAULT_FLAG_THRESHOLD = 0.80;

    /**
     * Minimum token length.  Tokens shorter than this are discarded before
     * fingerprinting (removes noise from very short identifiers/keywords).
     */
    public static final int DEFAULT_MIN_TOKEN_LENGTH = 3;

    /**
     * Winnowing window size for fingerprint selection.
     * Larger window → fewer fingerprints → faster but slightly less sensitive.
     */
    public static final int DEFAULT_WINDOW_SIZE = 4;

    /**
     * k-gram size used when building fingerprints.
     * Typical academic plagiarism detectors use k=5..8.
     */
    public static final int DEFAULT_KGRAM_SIZE = 5;

    // ── Fields ────────────────────────────────────────────────────────────────

    private final double flagThreshold;
    private final int    minTokenLength;
    private final int    windowSize;
    private final int    kgramSize;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** Fully-specified constructor. */
    public PlagiarismConfig(double flagThreshold,
                            int    minTokenLength,
                            int    windowSize,
                            int    kgramSize) {
        if (flagThreshold < 0.0 || flagThreshold > 1.0)
            throw new IllegalArgumentException("flagThreshold must be in [0,1]");
        if (minTokenLength < 1) throw new IllegalArgumentException("minTokenLength must be >= 1");
        if (windowSize     < 1) throw new IllegalArgumentException("windowSize must be >= 1");
        if (kgramSize      < 1) throw new IllegalArgumentException("kgramSize must be >= 1");

        this.flagThreshold  = flagThreshold;
        this.minTokenLength = minTokenLength;
        this.windowSize     = windowSize;
        this.kgramSize      = kgramSize;
    }

    /** Default-configuration constructor. */
    public PlagiarismConfig() {
        this(DEFAULT_FLAG_THRESHOLD,
             DEFAULT_MIN_TOKEN_LENGTH,
             DEFAULT_WINDOW_SIZE,
             DEFAULT_KGRAM_SIZE);
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public double getFlagThreshold()  { return flagThreshold; }
    public int    getMinTokenLength() { return minTokenLength; }
    public int    getWindowSize()     { return windowSize; }
    public int    getKgramSize()      { return kgramSize; }
}
