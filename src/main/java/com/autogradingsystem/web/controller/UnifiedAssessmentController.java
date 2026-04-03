package com.autogradingsystem.web.controller;

import com.autogradingsystem.multiassessment.AssessmentPathConfig;
import com.autogradingsystem.web.service.AssessmentProgressRegistry;
import com.autogradingsystem.web.service.GradingService;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

/**
 * UnifiedAssessmentController — Unified entry point for 1–5 assessments.
 *
 * ROUTES:
 *   POST   /assessments/upload                    — Upload 1–5 assessments
 *   GET    /assessments/status                    — List assessments with readiness flags
 *   POST   /assessments/{name}/parse-exam-marks   — LLM reads exam PDF
 *   POST   /assessments/{name}/generate-testers   — LLM generates tester source (review only)
 *   POST   /assessments/{name}/save-testers       — Save examiner-edited testers
 *   POST   /assessments/{name}/grade              — Run full 7-phase pipeline
 *   GET    /assessments/{name}/download           — Download report (?file=csv|scoresheet|plagiarism)
 *   POST   /assessments/clear                     — Clear all or one assessment
 *
 * OUTPUTS per assessment:
 *   IS442-ScoreSheet-Updated.xlsx  — Score Sheet + Anomalies + all statistics tabs
 *   IS442-ScoreSheet-Updated.csv   — CSV sidecar (used by status checks)
 *   IS442-Plagiarism-Report.xlsx   — Plagiarism flagged + all-pairs report
 *
 * NOTE: IS442-Statistics.xlsx is no longer produced. Statistics tabs are
 * embedded directly inside IS442-ScoreSheet-Updated.xlsx.
 */
@RestController
public class UnifiedAssessmentController {

    private static final int MAX_ASSESSMENTS = 5;

    // ── POST /assessments/upload ──────────────────────────────────────────────

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
            Set<String> seenAssessmentNames = new HashSet<>();

            for (int i = 0; i < names.size(); i++) {
                String name = names.get(i).trim();
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("name", name);

                AssessmentPathConfig paths = AssessmentPathConfig.forName(name);
                String sanitisedName = paths.getAssessmentName();
                if (!seenAssessmentNames.add(sanitisedName)) {
                    response.put("success", false);
                    response.put("message", "Duplicate assessment name after sanitisation: " + name);
                    return ResponseEntity.badRequest().body(response);
                }

                if (Files.isDirectory(paths.getAssessmentRoot())) {
                    deleteRecursive(paths.getAssessmentRoot());
                }
                paths.ensureDirectories();
                result.put("sanitisedName", sanitisedName);

                List<String> saved   = new ArrayList<>();
                List<String> missing = new ArrayList<>();

                validateAssessmentUpload(name, "submission", fileAt(submissions, i));
                validateAssessmentUpload(name, "template", fileAt(templates, i));
                validateAssessmentUpload(name, "scoresheet", fileAt(scoresheets, i));
                validateAssessmentUpload(name, "examPdf", fileAt(examPdfs, i));
                validateAssessmentUpload(name, "testers", fileAt(testerZips, i));

                List<String> missingRequired = missingRequiredUploads(
                        fileAt(submissions, i),
                        fileAt(templates, i),
                        fileAt(scoresheets, i),
                        fileAt(examPdfs, i));
                if (!missingRequired.isEmpty()) {
                    throw new IllegalArgumentException("Assessment \"" + name + "\" is missing required file(s): "
                            + String.join(", ", missingRequired) + ". Testers ZIP is optional.");
                }

                if (hasFile(submissions, i)) {
                    saveFile(submissions.get(i), paths.INPUT_SUBMISSIONS.resolve("student-submission.zip"));
                    saved.add("submission");
                } else { missing.add("submission"); }

                if (hasFile(templates, i)) {
                    saveFile(templates.get(i), paths.INPUT_TEMPLATE.resolve("RenameToYourUsername.zip"));
                    saved.add("template");
                } else { missing.add("template"); }

                if (hasFile(scoresheets, i)) {
                    saveFile(scoresheets.get(i), paths.CSV_SCORESHEET);
                    saved.add("scoresheet");
                } else { missing.add("scoresheet"); }

                if (hasFile(examPdfs, i)) {
                    String examFilename = examPdfs.get(i).getOriginalFilename();
                    saveFile(examPdfs.get(i),
                            paths.INPUT_EXAM.resolve(examFilename != null ? examFilename : "exam.pdf"));
                    saved.add("examPdf");
                } else { missing.add("examPdf"); }

                if (hasFile(testerZips, i)) {
                    extractZip(testerZips.get(i), paths.INPUT_TESTERS);
                    flattenDirectory(paths.INPUT_TESTERS);
                    saved.add("testers");
                }

                boolean hasTesters = Files.isDirectory(paths.INPUT_TESTERS) && hasJavaFiles(paths.INPUT_TESTERS);

                result.put("saved",   saved);
                result.put("missing", missing);
                result.put("hasTesters", hasTesters);
                result.put("ready",   missing.isEmpty() ||
                        (missing.size() == 1 && missing.contains("examPdf") && hasTesters));
                assessmentResults.add(result);
            }

            response.put("success",     true);
            response.put("assessments", assessmentResults);
            response.put("message",     assessmentResults.size() + " assessment(s) uploaded.");
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Upload failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // ── GET /assessments/status ───────────────────────────────────────────────

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
                        info.put("name",          name);
                        info.put("sanitisedName", name);
                        info.put("hasSubmission", Files.isDirectory(paths.INPUT_SUBMISSIONS) && hasFilesIn(paths.INPUT_SUBMISSIONS));
                        info.put("hasTemplate",   Files.isDirectory(paths.INPUT_TEMPLATE)   && hasFilesIn(paths.INPUT_TEMPLATE));
                        info.put("hasScoresheet", Files.exists(paths.CSV_SCORESHEET));
                        info.put("hasExamPdf",    Files.isDirectory(paths.INPUT_EXAM)       && hasPdfIn(paths.INPUT_EXAM));
                        info.put("hasTesters",    Files.isDirectory(paths.INPUT_TESTERS)    && hasJavaFiles(paths.INPUT_TESTERS));
                        info.put("hasReports",
                                Files.exists(paths.OUTPUT_REPORTS.resolve("IS442-ScoreSheet-Updated.xlsx"))
                             || Files.exists(paths.OUTPUT_REPORTS.resolve("IS442-ScoreSheet-Updated.csv")));
                        info.put("ready", (boolean) info.get("hasSubmission")
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

    // ── POST /assessments/{name}/parse-exam-marks ─────────────────────────────

    @PostMapping("/assessments/{name}/parse-exam-marks")
    public ResponseEntity<Map<String, Object>> parseExamMarks(@PathVariable String name) {
        AssessmentPathConfig paths = new AssessmentPathConfig(name);
        TestCaseReviewController reviewer = new TestCaseReviewController(
                paths.INPUT_TESTERS, paths.INPUT_TEMPLATE, paths.INPUT_EXAM);
        return reviewer.parseExamMarks();
    }

    // ── POST /assessments/{name}/generate-testers ─────────────────────────────

    @PostMapping("/assessments/{name}/generate-testers")
    public ResponseEntity<Map<String, Object>> generateTesters(
            @PathVariable String name,
            @RequestBody Map<String, Object> body) {
        AssessmentPathConfig paths = new AssessmentPathConfig(name);
        TestCaseReviewController reviewer = new TestCaseReviewController(
                paths.INPUT_TESTERS, paths.INPUT_TEMPLATE, paths.INPUT_EXAM);
        return reviewer.generateTesters(body);
    }

    // ── POST /assessments/{name}/save-testers ────────────────────────────────

    @PostMapping("/assessments/{name}/save-testers")
    public ResponseEntity<Map<String, Object>> saveTesters(
            @PathVariable String name,
            @RequestBody Map<String, Object> body) {
        AssessmentPathConfig paths = new AssessmentPathConfig(name);
        TestCaseReviewController reviewer = new TestCaseReviewController(
                paths.INPUT_TESTERS, paths.INPUT_TEMPLATE, paths.INPUT_EXAM);
        return reviewer.saveTesters(body);
    }

    // ── POST /assessments/{name}/grade ────────────────────────────────────────

    @PostMapping("/assessments/{name}/grade")
    public ResponseEntity<Map<String, Object>> grade(
            @PathVariable String name,
            @RequestParam(value = "applyPenalties", defaultValue = "false") boolean applyPenalties) {

        Map<String, Object> response = new LinkedHashMap<>();
        try {
            AssessmentPathConfig paths = new AssessmentPathConfig(name);
            GradingService service = new GradingService(paths);
            AssessmentProgressRegistry.start(name);

            // Pass the applyPenalties flag to the pipeline
            GradingService.GradingReport report = service.runFullPipeline(name, applyPenalties);

            if (report.isSuccess()) {
                AssessmentProgressRegistry.complete(name, "Grading completed successfully.");
            } else {
                AssessmentProgressRegistry.fail(name, "Grading completed with errors.");
            }

            response.put("success",         report.isSuccess());
            response.put("studentCount",    report.getStudentCount());
            response.put("logs",            report.getLogs());
            response.put("assessment",      name);
            response.put("penaltiesApplied", applyPenalties);

            if (report.isSuccess() && !report.getResults().isEmpty()) {
                double avg = report.getResults().stream()
                        .mapToDouble(r -> r.getScore())
                        .average().orElse(0);
                response.put("averageScore", String.format("%.1f", avg));
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            AssessmentProgressRegistry.fail(name, "Grading failed: " + e.getMessage());
            response.put("success", false);
            response.put("message", "Grading failed: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/assessments/{name}/progress")
    public ResponseEntity<Map<String, Object>> progress(@PathVariable String name) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("assessment", name);
        response.putAll(AssessmentProgressRegistry.snapshot(name));
        return ResponseEntity.ok(response);
    }

    // ── GET /assessments/{name}/download ──────────────────────────────────────

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
            case "plagiarism":
                reportFile = paths.OUTPUT_REPORTS.resolve("IS442-Plagiarism-Report.xlsx");
                break;
            default:
                return ResponseEntity.badRequest().build();
        }

        if (!Files.exists(reportFile)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(Objects.requireNonNull(reportFile.toFile()));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + reportFile.getFileName() + "\"")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_OCTET_STREAM))
                .body(resource);
    }

    // ── POST /assessments/clear ───────────────────────────────────────────────

    @PostMapping("/assessments/clear")
    public ResponseEntity<Map<String, Object>> clear(
            @RequestParam(value = "assessment", required = false) String assessment) {

        Map<String, Object> response = new LinkedHashMap<>();
        try {
            Path root = Paths.get("resources", "assessments");

            if (assessment != null && !assessment.isBlank()) {
                String sanitised = AssessmentPathConfig.forName(assessment).getAssessmentName();
                Path target = root.resolve(sanitised);
                if (Files.isDirectory(target)) {
                    deleteRecursive(target);
                    AssessmentProgressRegistry.clear(sanitised);
                    response.put("message", "Cleared assessment: " + sanitised);
                } else {
                    response.put("message", "Assessment not found: " + sanitised);
                }
            } else {
                if (Files.isDirectory(root)) {
                    try (Stream<Path> dirs = Files.list(root)) {
                        dirs.filter(Files::isDirectory).forEach(this::deleteRecursive);
                    }
                }
                AssessmentProgressRegistry.clearAll();
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

    // ── PRIVATE HELPERS ───────────────────────────────────────────────────────

    private boolean hasFile(List<MultipartFile> files, int index) {
        return files != null && index < files.size()
                && files.get(index) != null && !files.get(index).isEmpty();
    }

    private MultipartFile fileAt(List<MultipartFile> files, int index) {
        if (files == null || index >= files.size()) {
            return null;
        }
        MultipartFile file = files.get(index);
        return file == null || file.isEmpty() ? null : file;
    }

    private List<String> missingRequiredUploads(MultipartFile submission,
                                                MultipartFile template,
                                                MultipartFile scoresheet,
                                                MultipartFile examPdf) {
        List<String> missing = new ArrayList<>();
        if (submission == null) {
            missing.add("Submissions ZIP");
        }
        if (template == null) {
            missing.add("Template ZIP");
        }
        if (scoresheet == null) {
            missing.add("Score Sheet CSV");
        }
        if (examPdf == null) {
            missing.add("Exam PDF");
        }
        return missing;
    }

    private void validateAssessmentUpload(String assessmentName,
                                          String placeholder,
                                          MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return;
        }

        UploadSignature signature = inspectUpload(file);
        String originalName = file.getOriginalFilename() == null ? "(unnamed file)" : file.getOriginalFilename();

        switch (placeholder) {
            case "scoresheet":
                if (!signature.isCsv) {
                    throw new IllegalArgumentException("Assessment \"" + assessmentName + "\": \"" + originalName
                            + "\" was uploaded into Score Sheet, but it does not look like a score sheet CSV.");
                }
                break;
            case "examPdf":
                if (!signature.isPdf) {
                    throw new IllegalArgumentException("Assessment \"" + assessmentName + "\": \"" + originalName
                            + "\" was uploaded into Exam PDF, but it is not a PDF file.");
                }
                break;
            case "testers":
                if (!signature.isZip) {
                    throw new IllegalArgumentException("Assessment \"" + assessmentName + "\": \"" + originalName
                            + "\" was uploaded into Testers, but testers must be provided as a ZIP.");
                }
                if (!signature.hasTesterJava) {
                    throw new IllegalArgumentException("Assessment \"" + assessmentName + "\": \"" + originalName
                            + "\" was uploaded into Testers, but it does not contain any *Tester.java files.");
                }
                break;
            case "template":
                if (!signature.isZip) {
                    throw new IllegalArgumentException("Assessment \"" + assessmentName + "\": \"" + originalName
                            + "\" was uploaded into Template, but template must be a ZIP.");
                }
                if (signature.hasTesterJava) {
                    throw new IllegalArgumentException("Assessment \"" + assessmentName + "\": \"" + originalName
                            + "\" looks like a testers ZIP, not the template ZIP. Please move it to the Testers placeholder.");
                }
                if (!signature.hasJavaFiles) {
                    throw new IllegalArgumentException("Assessment \"" + assessmentName + "\": \"" + originalName
                            + "\" was uploaded into Template, but no Java question files were found inside.");
                }
                break;
            case "submission":
                if (!signature.isZip) {
                    throw new IllegalArgumentException("Assessment \"" + assessmentName + "\": \"" + originalName
                            + "\" was uploaded into Submissions, but submissions must be provided as a ZIP.");
                }
                if (signature.hasTesterJava) {
                    throw new IllegalArgumentException("Assessment \"" + assessmentName + "\": \"" + originalName
                            + "\" looks like a testers ZIP, not a student submissions ZIP.");
                }
                if (signature.hasJavaFiles && !signature.hasNestedZip) {
                    throw new IllegalArgumentException("Assessment \"" + assessmentName + "\": \"" + originalName
                            + "\" looks like a template ZIP, not a student submissions ZIP.");
                }
                break;
            default:
                break;
        }
    }

    private UploadSignature inspectUpload(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        UploadSignature signature = new UploadSignature();
        signature.isPdf = startsWith(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII));
        signature.isZip = startsWith(bytes, new byte[]{'P', 'K', 3, 4})
                || startsWith(bytes, new byte[]{'P', 'K', 5, 6})
                || startsWith(bytes, new byte[]{'P', 'K', 7, 8});

        String originalName = Objects.requireNonNullElse(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
        String text = new String(bytes, StandardCharsets.UTF_8);
        signature.isCsv = originalName.endsWith(".csv")
                || text.contains("Username")
                || text.contains("Email")
                || text.contains("OrgDefinedId");

        if (signature.isZip) {
            inspectZipEntries(bytes, signature);
        }
        return signature;
    }

    private void inspectZipEntries(byte[] bytes, UploadSignature signature) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String entryName = entry.getName();
                String lower = entryName.toLowerCase(Locale.ROOT);
                if (lower.endsWith(".zip")) {
                    signature.hasNestedZip = true;
                }
                if (lower.endsWith(".java")) {
                    signature.hasJavaFiles = true;
                }
                if (lower.endsWith("tester.java")) {
                    signature.hasTesterJava = true;
                }
            }
        } catch (ZipException e) {
            throw new IllegalArgumentException("Uploaded ZIP file could not be read: " + e.getMessage(), e);
        }
    }

    private boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes == null || prefix == null || bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static class UploadSignature {
        private boolean isPdf;
        private boolean isZip;
        private boolean isCsv;
        private boolean hasJavaFiles;
        private boolean hasTesterJava;
        private boolean hasNestedZip;
    }

    private void saveFile(MultipartFile file, Path destination) throws IOException {
        Path absolute = destination.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        Files.copy(file.getInputStream(), absolute, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("  💾 Saved: " + absolute);
    }

    private void extractZip(MultipartFile zipFile, Path destination) throws IOException {
        Files.createDirectories(destination);
        try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String entryName = entry.getName();
                if (entryName.contains("__MACOSX") || entryName.contains(".DS_Store")) continue;
                Path outPath = destination.resolve(entryName).normalize();
                if (!outPath.startsWith(destination)) continue;
                Files.createDirectories(outPath.getParent());
                Files.copy(zis, outPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void flattenDirectory(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".java"))
                .filter(p -> !p.getParent().equals(dir))
                .forEach(p -> {
                    try { Files.move(p, dir.resolve(p.getFileName()), StandardCopyOption.REPLACE_EXISTING); }
                    catch (IOException ignored) {}
                });
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                .filter(Files::isDirectory)
                .filter(p -> !p.equals(dir))
                .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        }
    }

    private boolean hasFilesIn(Path dir) {
        try (Stream<Path> s = Files.list(dir)) { return s.findAny().isPresent(); }
        catch (Exception e) { return false; }
    }

    private boolean hasPdfIn(Path dir) {
        try (Stream<Path> s = Files.list(dir)) {
            return s.anyMatch(p -> p.toString().toLowerCase().endsWith(".pdf"));
        } catch (Exception e) { return false; }
    }

    private boolean hasJavaFiles(Path dir) {
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile)
                    .anyMatch(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".java"));
        } catch (Exception e) { return false; }
    }

    private void deleteRecursive(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        } catch (Exception ignored) {}
    }
}
