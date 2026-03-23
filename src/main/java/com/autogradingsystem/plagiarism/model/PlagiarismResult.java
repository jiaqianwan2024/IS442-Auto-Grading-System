package com.autogradingsystem.plagiarism.model;

/**
 * PlagiarismResult
 *
 * Represents a single pairwise plagiarism comparison between two student
 * submissions for one question.
 *
 * Immutable after construction — use the builder-style factory method.
 */
public class PlagiarismResult {

    // ── Fields ────────────────────────────────────────────────────────────────

    private final String studentA;
    private final String studentB;
    private final String questionId;

    /** Normalised similarity score in [0.0, 1.0]. */
    private final double similarityScore;

    /** Similarity percentage rounded to 1 decimal place. */
    private final double similarityPercent;

    /** True when similarityScore >= the configured suspicion threshold. */
    private final boolean flagged;

    /** Human-readable summary of what was detected (for reports / remarks). */
    private final String summary;

    // ── Constructor ───────────────────────────────────────────────────────────

    public PlagiarismResult(String studentA,
                            String studentB,
                            String questionId,
                            double similarityScore,
                            boolean flagged,
                            String summary) {
        this.studentA         = studentA;
        this.studentB         = studentB;
        this.questionId       = questionId;
        this.similarityScore  = Math.min(1.0, Math.max(0.0, similarityScore));
        this.similarityPercent = Math.round(this.similarityScore * 1000.0) / 10.0;
        this.flagged          = flagged;
        this.summary          = summary;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getStudentA()          { return studentA; }
    public String getStudentB()          { return studentB; }
    public String getQuestionId()        { return questionId; }
    public double getSimilarityScore()   { return similarityScore; }
    public double getSimilarityPercent() { return similarityPercent; }
    public boolean isFlagged()           { return flagged; }
    public String getSummary()           { return summary; }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns the pair key used as a map key / deduplication token.
     * Always canonical (alphabetically ordered) so (A,B) == (B,A).
     */
    public String getPairKey() {
        String lo = studentA.compareTo(studentB) <= 0 ? studentA : studentB;
        String hi = lo.equals(studentA)             ? studentB : studentA;
        return lo + "|" + hi + "|" + questionId;
    }

    @Override
    public String toString() {
        return String.format("PlagiarismResult{%s vs %s, %s, %.1f%%, flagged=%b}",
                studentA, studentB, questionId, similarityPercent, flagged);
    }
}
