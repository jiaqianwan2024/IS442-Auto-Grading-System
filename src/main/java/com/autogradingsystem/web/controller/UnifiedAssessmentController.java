package com.autogradingsystem.web.controller;

import com.autogradingsystem.multiassessment.AssessmentPathConfig;
import com.autogradingsystem.web.service.GradingService;
import com.autogradingsystem.testcasegenerator.service.ExamPaperParser;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * UnifiedAssessmentController - Unified entry point for 1–5 assessments.
 *
 * REPLACES the separate Single Assessment + Multi Assessment tabs with ONE flow:
 *
 *   Landing page:  Upload 1–5 assessments (each with submissions, template,
 *                  scoresheet, exam PDF, and optionally a testers ZIP).
 *
 *   Per assessment: Each assessment gets its own tab running the full 4-step
 *                   single-assessment wizard:
 *                     Step 1 — Upload (done from landing page)
 *                     Step 2 — Confirm Marks   → POST /assessments/{name}/parse-exam-marks
 *                     Step 3 — Review Testers  → POST /assessments/{name}/generate-testers
 *                                              → POST /assessments/{name}/save-testers
 *                     Step 4 — Grade           → POST /assessments/{name}/grade
 *
 * ROUTES:
 *   POST   /assessments/upload              — Upload 1–5 assessments in one request
 *   GET    /assessments/status              — List all assessments with readiness flags
 *   POST   /assessments/{name}/parse-exam-marks   — LLM reads exam PDF for one assessment
 *   POST   /assessments/{name}/generate-testers   — LLM generates testers for one assessment
 *   POST   /assessments/{name}/save-testers       — Save examiner-edited testers for one assessment
 *   POST   /assessments/{name}/grade              — Run full pipeline for one assessment
 *   GET    /assessments/{name}/download           — Download report for one assessment
 *   POST   /assessments/clear                     — Clear all or one assessment
 *
 * DESIGN:
 *   - Each assessment is stored under resources/assessments/<sanitised-name>/
 *   - Per-assessment endpoints instantiate path-aware controllers/services
 *     using AssessmentPathConfig so each assessment is fully isolated.
 *   - The existing single-assessment flow (GradingController, TestCaseReviewController)
 *     is completely unchanged and still available at the old routes.
 *
 * @author IS442 Team
 * @version 4.0 (Unified Assessment)
 */
@RestController
public class UnifiedAssessmentController {

    // Maximum number of assessments allowed in one upload
    private static final int MAX_ASSESSMENTS = 5;

    // =====================================================================
    // POST /assessments/upload — Upload 1–5 assessments
    // =====================================================================

    /**
     * Accepts N sets of files (1 ≤ N ≤ 5) in a single multipart request.
     *
     * Form params (all are lists, matched by index):
     *   name[]        — assessment names, e.g. "Midterm 2526", "Lab Test 1"
     *   submission[]   — student-submission.zip per assessment
     *   template[]     — RenameToYourUsername.zip per assessment
     *   scoresheet[]   — IS442-ScoreSheet.csv per assessment
     *   examPdf[]      — exam-paper.pdf per assessment
     *   testers[]      — (optional) pre-made testers ZIP per assessment
     */
    @PostMapping("/assessments/upload")
    public ResponseEntity<Map<String, Object>> handleUpload(
            @RequestParam(value = "name",       required = false) List<String>        names,
            @RequestParam(value = "submission",  required = false) List<MultipartFile> submissions,
            @RequestParam(value = "template",   required = false) List<MultipartFile> templates,
            @RequestParam(value = "scoresheet", required = false) List<MultipartFile> scoresheets,
            @RequestParam(value = "examPdf",    required = false) List<MultipartFile> examPdfs,
            @RequestParam(value = "testers",    required = false) List<MultipartFile> testerZips) {

        Map<String, Object> response = new LinkedHashMap<>();

        try {
            if (names == null || names.isEmpty()) {
                response.put("success", false);
                response.put("message", "No assessment names provided.");
                return ResponseEntity.badRequest().body(response);
            }

            if (names.size() > MAX_ASSESSMENTS) {
                response.put("success", false);
                response.put("message", "Maximum " + MAX_ASSESSMENTS + " assessments allowed.");
                return ResponseEntity.badRequest().body(response);
            }

            List<Map<String, Object>> assessmentResults = new ArrayList<>();

            for (int i = 0; i < names.size(); i++) {
                String name = names.get(i).trim();
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("name", name);

                AssessmentPathConfig paths = AssessmentPathConfig.forName(name);
                paths.ensureDirectories();
                result.put("sanitisedName", paths.getAssessmentName());

                List<String> saved   = new ArrayList<>();
                List<String> missing = new ArrayList<>();

                // Submission ZIP
                if (hasFile(submissions, i)) {
                    saveFile(submissions.get(i),
                            paths.INPUT_SUBMISSIONS.resolve("student-submission.zip"));
                    saved.add("submission");
                } else {
                    missing.add("submission");
                }

                // Template ZIP
                if (hasFile(templates, i)) {
                    saveFile(templates.get(i),
                            paths.INPUT_TEMPLATE.resolve("RenameToYourUsername.zip"));
                    saved.add("template");
                } else {
                    missing.add("template");
                }

                // Scoresheet CSV
                if (hasFile(scoresheets, i)) {
                    saveFile(scoresheets.get(i), paths.CSV_SCORESHEET);
                    saved.add("scoresheet");
                } else {
                    missing.add("scoresheet");
                }

                // Exam PDF
                if (hasFile(examPdfs, i)) {
                    saveFile(examPdfs.get(i),
                            paths.INPUT_EXAM.resolve(examPdfs.get(i).getOriginalFilename()));
                    saved.add("examPdf");
                } else {
                    missing.add("examPdf");
                }

                // Testers ZIP (optional — examiner can generate via LLM instead)
                if (hasFile(testerZips, i)) {
                    extractZip(testerZips.get(i), paths.INPUT_TESTERS);
                    flattenDirectory(paths.INPUT_TESTERS);
                    saved.add("testers");
                }

                result.put("saved",   saved);
                result.put("missing", missing);
                result.put("ready",   missing.isEmpty() || 
                        (missing.size() == 1 && missing.contains("examPdf") && saved.contains("testers")));

                assessmentResults.add(result);
            }

            response.put("success",     true);
            response.put("assessments", assessmentResults);
            response.put("message",     assessmentResults.size() + " assessment(s) uploaded.");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Upload failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // =====================================================================
    // GET /assessments/status — List all uploaded assessments
    // =====================================================================

    @GetMapping("/assessments/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> response = new LinkedHashMap<>();
        try {
            Path root = Paths.get("resources", "assessments");
            List<Map<String, Object>> assessments = new ArrayList<>();

            if (Files.isDirectory(root)) {
                try (Stream<Path> dirs = Files.list(root)) {
                    dirs.filter(Files::isDirectory).sorted().forEach(dir -> {
                        String name = dir.getFileName().toString();
                        AssessmentPathConfig paths = new AssessmentPathConfig(name);

                        Map<String, Object> info = new LinkedHashMap<>();
                        info.put("name",           name);
                        info.put("sanitisedName",  name);
                        info.put("hasSubmission",  Files.isDirectory(paths.INPUT_SUBMISSIONS) && hasFilesIn(paths.INPUT_SUBMISSIONS));
                        info.put("hasTemplate",    Files.isDirectory(paths.INPUT_TEMPLATE) && hasFilesIn(paths.INPUT_TEMPLATE));
                        info.put("hasScoresheet",  Files.exists(paths.CSV_SCORESHEET));
                        info.put("hasExamPdf",     Files.isDirectory(paths.INPUT_EXAM) && hasPdfIn(paths.INPUT_EXAM));
                        info.put("hasTesters",     Files.isDirectory(paths.INPUT_TESTERS) && hasJavaFiles(paths.INPUT_TESTERS));
                        info.put("hasReports",     Files.exists(paths.OUTPUT_REPORTS.resolve("IS442-ScoreSheet-Updated.csv"))
                                                   || Files.exists(paths.OUTPUT_REPORTS.resolve("IS442-ScoreSheet-Updated.xlsx")));
                        info.put("ready",          (boolean) info.get("hasSubmission")
                                                   && (boolean) info.get("hasTemplate")
                                                   && (boolean) info.get("hasScoresheet"));
                        assessments.add(info);
                    });
                }
            }

            response.put("success",     true);
            response.put("assessments", assessments);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Status check failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // =====================================================================
    // POST /assessments/{name}/parse-exam-marks
    // =====================================================================

    /**
     * LLM reads the exam PDF for one assessment and returns detected marks.
     * Delegates to a path-aware TestCaseReviewController instance.
     */
    @PostMapping("/assessments/{name}/parse-exam-marks")
    public ResponseEntity<Map<String, Object>> parseExamMarks(@PathVariable String name) {
        AssessmentPathConfig paths = new AssessmentPathConfig(name);
        TestCaseReviewController reviewer = new TestCaseReviewController(
                paths.INPUT_TESTERS, paths.INPUT_TEMPLATE, paths.INPUT_EXAM);
        return reviewer.parseExamMarks();
    }

    // =====================================================================
    // POST /assessments/{name}/generate-testers
    // =====================================================================

    /**
     * Generates *Tester.java source for one assessment using confirmed marks.
     * Returns raw source for examiner review — does NOT write files.
     */
    @PostMapping("/assessments/{name}/generate-testers")
    public ResponseEntity<Map<String, Object>> generateTesters(
            @PathVariable String name,
            @RequestBody Map<String, Object> body) {
        AssessmentPathConfig paths = new AssessmentPathConfig(name);
        TestCaseReviewController reviewer = new TestCaseReviewController(
                paths.INPUT_TESTERS, paths.INPUT_TEMPLATE, paths.INPUT_EXAM);
        return reviewer.generateTesters(body);
    }

    // =====================================================================
    // POST /assessments/{name}/save-testers
    // =====================================================================

    /**
     * Saves examiner-edited tester source files for one assessment.
     */
    @PostMapping("/assessments/{name}/save-testers")
    public ResponseEntity<Map<String, Object>> saveTesters(
            @PathVariable String name,
            @RequestBody Map<String, Object> body) {
        AssessmentPathConfig paths = new AssessmentPathConfig(name);
        TestCaseReviewController reviewer = new TestCaseReviewController(
                paths.INPUT_TESTERS, paths.INPUT_TEMPLATE, paths.INPUT_EXAM);
        return reviewer.saveTesters(body);
    }

    // =====================================================================
    // POST /assessments/{name}/grade
    // =====================================================================

    /**
     * Runs the full 7-phase grading pipeline for one assessment.
     */
    @PostMapping("/assessments/{name}/grade")
    public ResponseEntity<Map<String, Object>> grade(@PathVariable String name) {
        Map<String, Object> response = new LinkedHashMap<>();
        try {
            AssessmentPathConfig paths = new AssessmentPathConfig(name);
            GradingService service = new GradingService(paths);
            GradingService.GradingReport report = service.runFullPipeline();

            response.put("success",      report.isSuccess());
            response.put("studentCount", report.getStudentCount());
            response.put("logs",         report.getLogs());
            response.put("assessment",   name);

            if (report.isSuccess() && !report.getResults().isEmpty()) {
                double avg = report.getResults().stream()
                        .mapToDouble(r -> r.getScore())
                        .average().orElse(0);
                response.put("averageScore", String.format("%.1f", avg));
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Grading failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // =====================================================================
    // GET /assessments/{name}/download?file=csv|scoresheet|excel|plagiarism
    // =====================================================================

    @GetMapping("/assessments/{name}/download")
    public ResponseEntity<Resource> download(
            @PathVariable String name,
            @RequestParam(value = "file", defaultValue = "scoresheet") String file) {

        AssessmentPathConfig paths = new AssessmentPathConfig(name);
        Path reportFile;

        switch (file.toLowerCase()) {
            case "csv":
                reportFile = paths.OUTPUT_REPORTS.resolve("IS442-ScoreSheet-Updated.csv");
                break;
            case "scoresheet":
                reportFile = paths.OUTPUT_REPORTS.resolve("IS442-ScoreSheet-Updated.xlsx");
                break;
            case "excel":
                reportFile = paths.OUTPUT_REPORTS.resolve("IS442-Statistics.xlsx");
                break;
            case "plagiarism":
                reportFile = paths.OUTPUT_REPORTS.resolve("IS442-Plagiarism-Report.xlsx");
                break;
            default:
                return ResponseEntity.badRequest().build();
        }

        if (!Files.exists(reportFile)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(reportFile.toFile());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + reportFile.getFileName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    // =====================================================================
    // POST /assessments/clear — Clear all or one assessment
    // =====================================================================

    @PostMapping("/assessments/clear")
    public ResponseEntity<Map<String, Object>> clear(
            @RequestParam(value = "assessment", required = false) String assessment) {

        Map<String, Object> response = new LinkedHashMap<>();
        try {
            Path root = Paths.get("resources", "assessments");

            if (assessment != null && !assessment.isBlank()) {
                // Clear one assessment
                Path target = root.resolve(assessment);
                if (Files.isDirectory(target)) {
                    deleteRecursive(target);
                    response.put("message", "Cleared assessment: " + assessment);
                } else {
                    response.put("message", "Assessment not found: " + assessment);
                }
            } else {
                // Clear all assessments
                if (Files.isDirectory(root)) {
                    try (Stream<Path> dirs = Files.list(root)) {
                        dirs.filter(Files::isDirectory).forEach(this::deleteRecursive);
                    }
                }
                response.put("message", "All assessments cleared.");
            }

            response.put("success", true);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Clear failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // =====================================================================
    // Private helpers
    // =====================================================================

    private boolean hasFile(List<MultipartFile> files, int index) {
        return files != null && index < files.size()
                && files.get(index) != null && !files.get(index).isEmpty();
    }

    private void saveFile(MultipartFile file, Path destination) throws IOException {
        Path absolute = destination.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        file.transferTo(absolute.toFile());
        System.out.println("  💾 Saved: " + absolute);
    }

    private void extractZip(MultipartFile zipFile, Path destination) throws IOException {
        Files.createDirectories(destination);
        try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;

                // Skip macOS metadata
                String entryName = entry.getName();
                if (entryName.contains("__MACOSX") || entryName.contains(".DS_Store")) continue;

                Path outPath = destination.resolve(entryName).normalize();
                // Security: prevent zip-slip
                if (!outPath.startsWith(destination)) continue;

                Files.createDirectories(outPath.getParent());
                Files.copy(zis, outPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void flattenDirectory(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return;

        // Move all .java files from subdirectories to root
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !p.getParent().equals(dir))
                .forEach(p -> {
                    try {
                        Path target = dir.resolve(p.getFileName());
                        Files.move(p, target, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException ignored) {}
                });
        }

        // Delete empty subdirectories
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                .filter(Files::isDirectory)
                .filter(p -> !p.equals(dir))
                .forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
        }
    }

    private boolean hasFilesIn(Path dir) {
        try (Stream<Path> s = Files.list(dir)) {
            return s.findAny().isPresent();
        } catch (Exception e) { return false; }
    }

    private boolean hasPdfIn(Path dir) {
        try (Stream<Path> s = Files.list(dir)) {
            return s.anyMatch(p -> p.toString().toLowerCase().endsWith(".pdf"));
        } catch (Exception e) { return false; }
    }

    private boolean hasJavaFiles(Path dir) {
        try (Stream<Path> s = Files.list(dir)) {
            return s.anyMatch(p -> p.toString().endsWith(".java"));
        } catch (Exception e) { return false; }
    }

    private void deleteRecursive(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
        } catch (Exception ignored) {}
    }
}