package com.autogradingsystem.testcasegenerator.controller;

import com.autogradingsystem.PathConfig;
import com.autogradingsystem.testcasegenerator.model.QuestionSpec;
import com.autogradingsystem.testcasegenerator.service.TesterGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

/**
 * TestCaseGeneratorController — orchestrates *Tester.java generation.
 *
 * PACKAGE: com.autogradingsystem.testcasegenerator.controller
 *
 * Two entry points:
 *
 *   generateAll()       — ALWAYS clears and regenerates. Called by /generate-testers
 *                         (the Web UI "Generate" button). This is the intentional
 *                         "force-regenerate" path used when the examiner wants
 *                         fresh AI output before reviewing.
 *
 *   generateIfNeeded()  — SKIPS generation when saved testers already exist on disk.
 *                         Called by GradingService.runFullPipeline() during /grade.
 *                         If the examiner has already saved testers via /save-testers,
 *                         those files are used as-is and this method is a no-op.
 *
 * FIX (Bug 1 — saved testers overwritten):
 *   Previously generateIfNeeded() called generateAll() unconditionally, which
 *   cleared the testers folder before writing new AI-generated files — wiping
 *   any manual edits the examiner had saved.
 *
 *   Now generateIfNeeded() returns immediately when any *Tester.java file is
 *   found in resources/input/testers/. generateAll() is unchanged and still
 *   used for the explicit "Generate Testers" button in the Web UI.
 */
public class TestCaseGeneratorController {

    private final TesterGenerator testerGenerator;

    public TestCaseGeneratorController() {
        this.testerGenerator = new TesterGenerator();
    }

    // -------------------------------------------------------------------------
    // generateAll — always clears + regenerates (used by /generate-testers)
    // -------------------------------------------------------------------------

    /**
     * Clears all existing *Tester.java files from resources/input/testers/,
     * then generates fresh ones from the LLM for every question in specs.
     *
     * Called by GradingController /generate-testers — the Web UI "Generate" button.
     * This is the intentional force-regenerate path and always overwrites.
     *
     * @return true if at least one tester was written successfully
     */
    public boolean generateAll(Map<String, QuestionSpec> specs,
                               Map<String, Integer> weights) throws IOException {

        Path testersDir = PathConfig.INPUT_TESTERS;
        Files.createDirectories(testersDir);

        // Clear existing testers
        clearTestersDir(testersDir);

        return generateInto(specs, weights, testersDir);
    }

    // -------------------------------------------------------------------------
    // generateIfNeeded — skips when saved testers already exist (used by /grade)
    // -------------------------------------------------------------------------

    /**
     * Generates *Tester.java files ONLY when none exist yet.
     *
     * If the examiner has already saved testers (via /save-testers or a prior
     * generateAll run), this method is a no-op and returns true immediately.
     * The saved files on disk — including any manual edits — are left untouched.
     *
     * Called by GradingService.runFullPipeline() during /grade.
     *
     * @return true always (either files already exist or generation succeeded)
     */
    public boolean generateIfNeeded(Map<String, QuestionSpec> specs,
                                    Map<String, Integer> weights) throws IOException {

        Path testersDir = PathConfig.INPUT_TESTERS;
        Files.createDirectories(testersDir);

        // ── FIX: skip if examiner-saved testers are present ──────────────────
        if (savedTestersExist(testersDir)) {
            System.out.println("⏭️  TestCaseGeneratorController.generateIfNeeded(): "
                    + "examiner-saved testers found — skipping AI generation.");
            return true;
        }
        // ── END FIX ──────────────────────────────────────────────────────────

        System.out.println("⚙️  TestCaseGeneratorController.generateIfNeeded(): "
                + "no testers found — generating from LLM...");
        return generateInto(specs, weights, testersDir);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Returns true when at least one *Tester.java file is present in testersDir.
     * This is the signal that the examiner has already reviewed and saved testers.
     */
    private boolean savedTestersExist(Path testersDir) {
        if (!Files.isDirectory(testersDir)) return false;
        try (Stream<Path> files = Files.list(testersDir)) {
            return files.anyMatch(p -> p.getFileName().toString().endsWith("Tester.java"));
        } catch (IOException e) {
            return false; // unreadable dir — fall through to generation
        }
    }

    /**
     * Deletes all *Tester.java files from the given directory.
     */
    private void clearTestersDir(Path testersDir) throws IOException {
        if (!Files.isDirectory(testersDir)) return;
        try (Stream<Path> files = Files.list(testersDir)) {
            files.filter(p -> p.getFileName().toString().endsWith("Tester.java"))
                 .forEach(p -> {
                     try {
                         Files.deleteIfExists(p);
                     } catch (IOException e) {
                         System.err.println("⚠️  Could not delete: " + p + " — " + e.getMessage());
                     }
                 });
        }
    }

    /**
     * Generates one *Tester.java per question in specs and writes it to testersDir.
     *
     * @return true if at least one file was written
     */
    private boolean generateInto(Map<String, QuestionSpec> specs,
                                  Map<String, Integer> weights,
                                  Path testersDir) throws IOException {
        if (specs == null || specs.isEmpty()) {
            System.err.println("⚠️  TestCaseGeneratorController: no question specs provided.");
            return false;
        }

        int written = 0;
        for (Map.Entry<String, QuestionSpec> entry : specs.entrySet()) {
            String questionId = entry.getKey();
            QuestionSpec spec = entry.getValue();
            int weight = weights != null ? weights.getOrDefault(questionId, 1) : 1;

            try {
                String source   = testerGenerator.generate(questionId, spec, weight);
                String filename = questionId + "Tester.java";
                Path   dest     = testersDir.resolve(filename);
                Files.writeString(dest, source);
                System.out.println("   ✅ Written: " + filename);
                written++;
            } catch (Exception e) {
                System.err.println("⚠️  Failed to generate tester for "
                        + questionId + ": " + e.getMessage());
            }
        }

        System.out.println("⚙️  TestCaseGeneratorController: " + written
                + "/" + specs.size() + " tester(s) written.");
        return written > 0;
    }
}