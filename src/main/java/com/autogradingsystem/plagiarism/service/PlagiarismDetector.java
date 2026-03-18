package com.autogradingsystem.plagiarism.service;

import com.autogradingsystem.model.GradingTask;
import com.autogradingsystem.plagiarism.model.PlagiarismConfig;
import com.autogradingsystem.plagiarism.model.PlagiarismResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * PlagiarismDetector
 *
 * PURPOSE:
 *   Core detection engine.  For each question in the grading plan it:
 *     1. Collects source files from all student submissions
 *     2. Normalises + fingerprints each file
 *     3. Performs all-pairs comparison (O(n²) pairs per question)
 *     4. Returns PlagiarismResult for every pair that meets the flag threshold
 *
 * The detector is stateless — all state is passed in and returned.
 * It is called once per grading run by PlagiarismController.
 */
public class PlagiarismDetector {

    private final CodeNormalizer      normalizer;
    private final FingerprintService  fingerprintService;
    private final SimilarityCalculator similarityCalc;

    public PlagiarismDetector() {
        this.normalizer        = new CodeNormalizer();
        this.fingerprintService = new FingerprintService();
        this.similarityCalc    = new SimilarityCalculator();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Runs plagiarism detection across all students for all tasks.
     *
     * @param extractedRoot  Path to resources/output/extracted/
     * @param tasks          Grading task list (one per question file)
     * @param config         Thresholds and algorithm parameters
     * @return All PlagiarismResult objects (flagged and unflagged)
     */
    public List<PlagiarismResult> detect(Path extractedRoot,
                                         List<GradingTask> tasks,
                                         PlagiarismConfig config) throws IOException {

        List<PlagiarismResult> allResults = new ArrayList<>();

        // Collect student folders
        List<Path> studentDirs = collectStudentDirs(extractedRoot);
        if (studentDirs.size() < 2) {
            System.out.println("⚠️  [Plagiarism] Fewer than 2 student submissions found — skipping.");
            return allResults;
        }

        System.out.println("\n🔍 [Plagiarism] Analysing " + studentDirs.size()
                + " submissions × " + tasks.size() + " questions …");

        for (GradingTask task : tasks) {
            // Only compare Java source files
            if (!task.getStudentFile().endsWith(".java")) continue;

            allResults.addAll(detectForQuestion(studentDirs, task, config));
        }

        long flaggedCount = allResults.stream().filter(PlagiarismResult::isFlagged).count();
        System.out.println("🔍 [Plagiarism] Done. "
                + allResults.size() + " pairs checked, "
                + flaggedCount + " flagged (threshold="
                + String.format("%.0f%%", config.getFlagThreshold() * 100) + ").");

        return allResults;
    }

    // ── Per-question logic ────────────────────────────────────────────────────

    private List<PlagiarismResult> detectForQuestion(List<Path> studentDirs,
                                                      GradingTask task,
                                                      PlagiarismConfig config) {

        // Build (student → fingerprints) map for this question
        Map<String, Set<Long>> fingerprintMap = new LinkedHashMap<>();

        for (Path studentDir : studentDirs) {
            String studentId = studentDir.getFileName().toString();
            Path   javaFile  = findFileRecursive(studentDir, task.getStudentFile());
            if (javaFile == null) continue;

            try {
                String source = Files.readString(javaFile);
                List<String> tokens = normalizer.normalize(source, config.getMinTokenLength());
                Set<Long> fps = fingerprintService.fingerprint(tokens,
                        config.getKgramSize(), config.getWindowSize());
                if (!fps.isEmpty()) {
                    fingerprintMap.put(studentId, fps);
                }
            } catch (IOException e) {
                System.out.println("⚠️  [Plagiarism] Cannot read "
                        + javaFile + ": " + e.getMessage());
            }
        }

        // All-pairs comparison
        List<String> ids = new ArrayList<>(fingerprintMap.keySet());
        List<PlagiarismResult> results = new ArrayList<>();

        for (int i = 0; i < ids.size(); i++) {
            for (int j = i + 1; j < ids.size(); j++) {
                String idA = ids.get(i);
                String idB = ids.get(j);

                Set<Long> fpsA = fingerprintMap.get(idA);
                Set<Long> fpsB = fingerprintMap.get(idB);

                double score   = similarityCalc.combined(fpsA, fpsB);
                boolean flagged = score >= config.getFlagThreshold();

                String summary = buildSummary(idA, idB, task.getQuestionId(),
                        score, fpsA, fpsB, flagged);

                results.add(new PlagiarismResult(idA, idB, task.getQuestionId(),
                        score, flagged, summary));

                if (flagged) {
                    System.out.println("   🚨 " + task.getQuestionId()
                            + "  " + idA + " ↔ " + idB
                            + "  similarity=" + String.format("%.1f%%", score * 100));
                }
            }
        }

        return results;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private List<Path> collectStudentDirs(Path extractedRoot) throws IOException {
        List<Path> dirs = new ArrayList<>();
        if (!Files.exists(extractedRoot)) return dirs;

        try (Stream<Path> stream = Files.list(extractedRoot)) {
            stream.filter(Files::isDirectory)
                  .filter(p -> !p.getFileName().toString().startsWith("__"))
                  .filter(p -> !p.getFileName().toString().startsWith("."))
                  .sorted()
                  .forEach(dirs::add);
        }
        return dirs;
    }

    private Path findFileRecursive(Path root, String filename) {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                       .filter(p -> p.getFileName().toString().equalsIgnoreCase(filename))
                       .findFirst()
                       .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private String buildSummary(String idA, String idB, String questionId,
                                 double score, Set<Long> fpsA, Set<Long> fpsB,
                                 boolean flagged) {
        long intersection = fpsA.stream().filter(fpsB::contains).count();
        return String.format(
            "%s: %s ↔ %s  combined=%.1f%%  shared_fingerprints=%d/%d+%d  flagged=%b",
            questionId, idA, idB,
            score * 100, intersection, fpsA.size(), fpsB.size(),
            flagged
        );
    }
}
