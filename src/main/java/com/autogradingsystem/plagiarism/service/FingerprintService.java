package com.autogradingsystem.plagiarism.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * FingerprintService
 *
 * PURPOSE:
 *   Implements the Winnowing algorithm (Schleimer, Wilkerson, Aiken 2003) to
 *   produce a compact, representative set of hash fingerprints from a token
 *   sequence.  Fingerprints are later compared between pairs of submissions.
 *
 * ALGORITHM OVERVIEW:
 *   1. Build all k-grams (overlapping subsequences of k tokens)
 *   2. Hash each k-gram with a rolling hash
 *   3. Slide a window of size w across the hash sequence
 *   4. In each window, select the minimum hash (ties: prefer rightmost)
 *   5. Add newly selected hashes to the fingerprint set
 *
 * WHY WINNOWING:
 *   - Guarantees that any shared substring of length >= k*w is detected
 *   - Produces a fixed-fraction sample → O(n/w) fingerprints regardless of file size
 *   - Robust to insertions/deletions between copied regions
 */
public class FingerprintService {

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Produces the Winnowing fingerprint set for the given token list.
     *
     * @param tokens     Normalised token list from CodeNormalizer
     * @param kgramSize  Number of tokens per k-gram
     * @param windowSize Winnowing window size
     * @return Set of integer fingerprint hashes (ordered insertion)
     */
    public Set<Long> fingerprint(List<String> tokens, int kgramSize, int windowSize) {
        Set<Long> fingerprints = new LinkedHashSet<>();

        if (tokens == null || tokens.size() < kgramSize) return fingerprints;

        // Step 1: build k-gram hashes
        List<Long> hashes = buildKgramHashes(tokens, kgramSize);

        if (hashes.size() < windowSize) {
            // Fewer hashes than window — just keep all
            fingerprints.addAll(hashes);
            return fingerprints;
        }

        // Step 2: Winnowing — slide window, select minimum
        long prevMin   = Long.MAX_VALUE;
        int  prevMinIdx = -1;

        for (int i = 0; i <= hashes.size() - windowSize; i++) {
            // Find minimum in window [i, i+windowSize)
            long minHash = Long.MAX_VALUE;
            int  minIdx  = -1;
            for (int j = i; j < i + windowSize; j++) {
                long h = hashes.get(j);
                if (h < minHash || (h == minHash && j > minIdx)) {
                    minHash = h;
                    minIdx  = j;
                }
            }

            // Add if the selected hash is new or from a different position
            if (minHash != prevMin || minIdx != prevMinIdx) {
                fingerprints.add(minHash);
                prevMin    = minHash;
                prevMinIdx = minIdx;
            }
        }

        return fingerprints;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Builds the list of k-gram hashes using a polynomial rolling hash.
     * Hash is computed mod 2^31-1 (a large prime) to reduce collisions.
     */
    private List<Long> buildKgramHashes(List<String> tokens, int k) {
        List<Long> hashes = new ArrayList<>();
        long prime = 2147483647L; // 2^31 - 1 (Mersenne prime)
        long base  = 31L;

        for (int i = 0; i <= tokens.size() - k; i++) {
            long hash = 0;
            for (int j = 0; j < k; j++) {
                hash = (hash * base + tokenHash(tokens.get(i + j))) % prime;
            }
            hashes.add(hash);
        }
        return hashes;
    }

    /**
     * Maps a token string to a stable positive long.
     * Uses Java's built-in hashCode, shifted to avoid negatives.
     */
    private long tokenHash(String token) {
        int h = token.hashCode();
        return h < 0 ? (long) Integer.MAX_VALUE - h : (long) h;
    }
}
