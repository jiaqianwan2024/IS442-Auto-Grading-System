package com.autogradingsystem.plagiarism.service;

import java.util.Set;

/**
 * SimilarityCalculator
 *
 * PURPOSE:
 *   Computes a similarity score in [0.0, 1.0] between two fingerprint sets
 *   using the Jaccard coefficient:
 *
 *       similarity = |A ∩ B| / |A ∪ B|
 *
 *   A score of 1.0 means the fingerprint sets are identical; 0.0 means no
 *   shared fingerprints at all.
 *
 * WHY JACCARD:
 *   - Symmetric — similarity(A,B) == similarity(B,A)
 *   - Naturally normalised — accounts for submissions of different lengths
 *   - Widely used in academic plagiarism detection literature
 *
 * Alternative: containment index max(|A∩B|/|A|, |A∩B|/|B|) is also computed
 *   and exposed for cases where a large submission is a superset of a small one.
 */
public class SimilarityCalculator {

    /**
     * Computes the Jaccard similarity between two fingerprint sets.
     *
     * @return Value in [0.0, 1.0]; returns 0.0 if both sets are empty
     */
    public double jaccard(Set<Long> fingerprintsA, Set<Long> fingerprintsB) {
        if (fingerprintsA.isEmpty() && fingerprintsB.isEmpty()) return 0.0;
        if (fingerprintsA.isEmpty() || fingerprintsB.isEmpty()) return 0.0;

        long intersection = fingerprintsA.stream()
                .filter(fingerprintsB::contains)
                .count();

        long union = fingerprintsA.size() + fingerprintsB.size() - intersection;
        return union == 0 ? 0.0 : (double) intersection / union;
    }

    /**
     * Computes the containment index: the larger of the two one-sided overlaps.
     *   containment = max( |A∩B|/|A| ,  |A∩B|/|B| )
     *
     * Useful when detecting partial copying — e.g., a student who copies a
     * function but adds their own code on top will have a low Jaccard but a
     * high containment in the smaller/shared direction.
     *
     * @return Value in [0.0, 1.0]; returns 0.0 if either set is empty
     */
    public double containment(Set<Long> fingerprintsA, Set<Long> fingerprintsB) {
        if (fingerprintsA.isEmpty() || fingerprintsB.isEmpty()) return 0.0;

        long intersection = fingerprintsA.stream()
                .filter(fingerprintsB::contains)
                .count();

        double containA = (double) intersection / fingerprintsA.size();
        double containB = (double) intersection / fingerprintsB.size();
        return Math.max(containA, containB);
    }

    /**
     * Combined score used for flagging decisions.
     * Weighs Jaccard (structural overlap) and containment (partial copying).
     *
     *   combined = 0.6 * jaccard + 0.4 * containment
     *
     * The weights are tuned so that:
     *   - Near-identical submissions score ≈ 1.0
     *   - Partial copying (one section copied) can still breach the threshold
     *   - Independent solutions with coincidentally similar structure score low
     */
    public double combined(Set<Long> fingerprintsA, Set<Long> fingerprintsB) {
        return 0.6 * jaccard(fingerprintsA, fingerprintsB)
             + 0.4 * containment(fingerprintsA, fingerprintsB);
    }
}
