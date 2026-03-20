package com.autogradingsystem.web.controller;

import com.autogradingsystem.multiassessment.AssessmentBundle;
import com.autogradingsystem.multiassessment.AssessmentOrchestrator;
import com.autogradingsystem.multiassessment.AssessmentOrchestrator.AssessmentReport;
import com.autogradingsystem.multiassessment.AssessmentOrchestrator.MultiAssessmentReport;
import com.autogradingsystem.multiassessment.AssessmentPathConfig;
import com.autogradingsystem.multiassessment.GradingProgressTracker;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MultiAssessmentController - Web Layer for Multi-Assessment Grading
 *
 * PACKAGE: com.autogradingsystem.web.controller
 *
 * PURPOSE:
 *   Provides HTTP endpoints that let the professor upload and grade multiple
 *   assessments (midterm, final, lab tests, etc.) in one request.
 *
 * ROUTES:
 *   POST /multi/upload   → Upload files for N assessments
 *   POST /multi/grade    → Grade all previously uploaded assessments concurrently
 *   GET  /multi/status   → List uploaded assessments and their readiness
 *   GET  /multi/download → Download a specific report file
 *
 * UPLOAD FORMAT (POST /multi/upload):
 *   Multipart form with repeating groups of 4 files per assessment:
 *
 *     assessment[0][name]        = "Midterm 2526"         (text field)
 *     assessment[0][submission]  = midterm-submissions.zip
 *     assessment[0][template]    = midterm-template.zip
 *     assessment[0][testers]     = midterm-testers.zip
 *     assessment[0][scoresheet]  = midterm-scoresheet.csv
 *
 *     assessment[1][name]        = "Lab Test 1"
 *     assessment[1][submission]  = lab1-submissions.zip
 *     ... (same 4 files)
 *
 *   The number of assessments is dynamic — the frontend sends however many
 *   the professor added. Each group is identified by its index [0], [1], etc.
 *
 * ISOLATION:
 *   Each assessment's files are saved to:
 *     resources/assessments/<sanitised-name>/input/...
 *   so they never interfere with each other or with the single-assessment flow.
 *
 * @author IS442 Team
 * @version 1.0 (Multi-Assessment Bonus Feature)
 */
@Controller
@RequestMapping("/multi")
public class MultiAssessmentController {

    private final AssessmentOrchestrator orchestrator;

    public MultiAssessmentController(AssessmentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    // ================================================================
    // UPLOAD — POST /multi/upload
    // ================================================================

    /**
     * Accepts a multipart upload containing files for one or more assessments.
     *
     * The frontend sends repeating parameter groups indexed by [0], [1], …
     * This endpoint iterates indices 0–19 (max 20 assessments per request)
     * and processes each group that has a name and at least one file.
     *
     * @param names        Assessment names, one per index (e.g. "Midterm 2526")
     * @param submissions  Student submission ZIPs, one per index
     * @param templates    Template ZIPs, one per index
     * @param testerZips   Tester ZIPs, one per index (extracted to testers/)
     * @param scoresheets  Scoresheet CSVs, one per index
     * @return JSON with upload summary and per-assessment status
     */
    @PostMapping("/upload")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleMultiUpload(
            @RequestParam(value = "name",       required = false) List<String>        names,
            @RequestParam(value = "submission", required = false) List<MultipartFile> submissions,
            @RequestParam(value = "template",   required = false) List<MultipartFile> templates,
            @RequestParam(value = "testers",    required = false) List<MultipartFile> testerZips,
            @RequestParam(value = "scoresheet", required = false) List<MultipartFile> scoresheets) {

        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> assessmentStatuses = new ArrayList<>();

        if (names == null || names.isEmpty()) {
            response.put("success", false);
            response.put("message", "No assessment names provided. "
                    + "Send at least one 'name' field.");
            return ResponseEntity.badRequest().body(response);
        }

        int processed = 0;
        int failed    = 0;

        for (int i = 0; i < names.size(); i++) {
            String rawName = names.get(i);
            if (rawName == null || rawName.isBlank()) continue;

            Map<String, Object> status = new LinkedHashMap<>();
            status.put("name", rawName);

            try {
                AssessmentPathConfig paths = AssessmentPathConfig.forName(rawName);
                paths.ensureDirectories();

                List<String> saved   = new ArrayList<>();
                List<String> missing = new ArrayList<>();

                // Save submission ZIP
                if (hasFile(submissions, i)) {
                    saveFile(submissions.get(i),
                            paths.INPUT_SUBMISSIONS.resolve("student-submission.zip"));
                    saved.add("submission");
                } else {
                    missing.add("submission");
                }

                // Save template ZIP
                if (hasFile(templates, i)) {
                    saveFile(templates.get(i),
                            paths.INPUT_TEMPLATE.resolve("RenameToYourUsername.zip"));
                    saved.add("template");
                } else {
                    missing.add("template");
                }

                // Save testers ZIP — extract contents to testers/
                if (hasFile(testerZips, i)) {
                    Path tempZip = paths.INPUT_BASE.resolve("testers-temp.zip");
                    saveFile(testerZips.get(i), tempZip);
                    clearDirectory(paths.INPUT_TESTERS);
                    extractZip(tempZip, paths.INPUT_TESTERS);
                    flattenDirectory(paths.INPUT_TESTERS);
                    Files.deleteIfExists(tempZip);
                    saved.add("testers");
                } else {
                    missing.add("testers");
                }

                // Save scoresheet CSV
                if (hasFile(scoresheets, i)) {
                    saveFile(scoresheets.get(i), paths.CSV_SCORESHEET);
                    saved.add("scoresheet");
                } else {
                    missing.add("scoresheet");
                }

                status.put("sanitisedName", paths.getAssessmentName());
                status.put("saved",   saved);
                status.put("missing", missing);
                status.put("ready",   missing.isEmpty());
                processed++;

            } catch (IOException e) {
                status.put("error", "Upload failed: " + e.getMessage());
                status.put("ready", false);
                failed++;
            }

            assessmentStatuses.add(status);
        }

        response.put("success",     failed == 0);
        response.put("processed",   processed);
        response.put("failed",      failed);
        response.put("assessments", assessmentStatuses);
        response.put("message",     processed + " assessment(s) uploaded"
                + (failed > 0 ? ", " + failed + " failed" : ""));

        return ResponseEntity.ok(response);
    }

    // ================================================================
    // GRADE — POST /multi/grade
    // ================================================================

    /**
     * Grades all assessments whose files were previously uploaded via
     * POST /multi/upload.
     *
     * Scans resources/assessments/ for subdirectories, wraps each in an
     * AssessmentBundle, and passes them to AssessmentOrchestrator.gradeAll().
     * The orchestrator runs all pipelines concurrently.
     *
     * @param names Optional list of specific assessment names to grade.
     *              If empty or absent, ALL uploaded assessments are graded.
     * @return JSON with per-assessment results and combined summary
     */
    @PostMapping("/grade")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> gradeAll(
            @RequestParam(value = "names", required = false) List<String> names) {

        Map<String, Object> response = new HashMap<>();

        try {
            // Discover uploaded assessments
            List<AssessmentBundle> bundles = discoverBundles(names);

            if (bundles.isEmpty()) {
                response.put("success", false);
                response.put("message", "No uploaded assessments found. "
                        + "Upload files first via POST /multi/upload.");
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
            }

            // Run all pipelines concurrently
            MultiAssessmentReport report = orchestrator.gradeAll(bundles);

            // Build JSON response
            response.put("success",        report.overallSuccess());
            response.put("totalStudents",  report.getTotalStudents());
            response.put("totalResults",   report.getTotalResults());
            response.put("successCount",   report.getSuccessCount());
            response.put("failCount",      report.getFailCount());
            response.put("logs",           report.getAllLogs());

            List<Map<String, Object>> assessmentSummaries = new ArrayList<>();
            for (AssessmentReport ar : report.getIndividualReports()) {
                Map<String, Object> summary = new LinkedHashMap<>();
                summary.put("assessmentName", ar.getAssessmentName());
                summary.put("success",        ar.isSuccess());
                summary.put("studentCount",   ar.getStudentCount());
                summary.put("resultCount",    ar.getResults().size());
                summary.put("logs",           ar.getLogs());
                if (!ar.isSuccess() && ar.getErrorMessage() != null) {
                    summary.put("error", ar.getErrorMessage());
                }

                // Per-assessment download links
                AssessmentPathConfig paths = new AssessmentPathConfig(ar.getAssessmentName());
                summary.put("downloadCsv",        "/multi/download?assessment="
                        + paths.getAssessmentName() + "&file=csv");
                summary.put("downloadScoreSheet", "/multi/download?assessment="
                        + paths.getAssessmentName() + "&file=scoresheet");
                summary.put("downloadStatistics", "/multi/download?assessment="
                        + paths.getAssessmentName() + "&file=excel");

                assessmentSummaries.add(summary);
            }

            response.put("assessments", assessmentSummaries);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Grading failed: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // ================================================================
    // STATUS — GET /multi/status
    // ================================================================

    /**
     * Returns the list of uploaded assessments and whether each has all
     * 4 required input files.
     *
     * Useful for the frontend to show a pre-flight checklist before grading.
     *
     * @return JSON with list of assessment readiness states
     */
    @GetMapping("/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> response = new HashMap<>();

        try {
            List<AssessmentBundle> bundles = discoverBundles(null);
            List<Map<String, Object>> statuses = new ArrayList<>();

            for (AssessmentBundle bundle : bundles) {
                AssessmentPathConfig p = bundle.getPathConfig();
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("name",          bundle.getAssessmentName());
                s.put("sanitisedName", p.getAssessmentName());
                s.put("ready",         bundle.isReady());
                s.put("hasSubmission", p.INPUT_SUBMISSIONS.toFile().exists()
                                       && hasZip(p.INPUT_SUBMISSIONS));
                s.put("hasTemplate",   p.INPUT_TEMPLATE.toFile().exists()
                                       && hasZip(p.INPUT_TEMPLATE));
                s.put("hasTesters",    p.INPUT_TESTERS.toFile().exists()
                                       && hasJavaFiles(p.INPUT_TESTERS));
                s.put("hasScoresheet", p.CSV_SCORESHEET.toFile().exists());
                s.put("hasReports",    p.OUTPUT_REPORTS.resolve("IS442-ScoreSheet-Updated.csv").toFile().exists());
                statuses.add(s);
            }

            response.put("assessments", statuses);
            response.put("totalReady",  statuses.stream()
                    .filter(s -> Boolean.TRUE.equals(s.get("ready"))).count());
            return ResponseEntity.ok(response);

        } catch (IOException e) {
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }


    // ================================================================
    // PROGRESS — GET /multi/progress
    // ================================================================

    /**
     * Server-Sent Events endpoint — streams grading progress to the browser.
     * The client connects once and receives JSON events every 500ms until done.
     */
    @GetMapping(value = "/progress", produces = "text/event-stream")
    public SseEmitter streamProgress() {
        SseEmitter emitter = new SseEmitter(300_000L); // 5-min timeout

        Thread thread = new Thread(() -> {
            try {
                GradingProgressTracker tracker = orchestrator.getTracker();
                while (true) {
                    GradingProgressTracker.ProgressSnapshot snap = tracker.snapshot();

                    // Build JSON manually — no Jackson dependency needed
                    StringBuilder json = new StringBuilder();
                    json.append("{");
                    json.append("\"overall\":").append(snap.overallPercent()).append(",");
                    json.append("\"done\":").append(snap.done()).append(",");
                    json.append("\"elapsed\":").append(snap.elapsedSeconds()).append(",");
                    json.append("\"assessments\":[");
                    List<GradingProgressTracker.AssessmentProgress> list = snap.assessments();
                    for (int i = 0; i < list.size(); i++) {
                        GradingProgressTracker.AssessmentProgress a = list.get(i);
                        if (i > 0) json.append(",");
                        json.append("{");
                        json.append("\"name\":\"").append(escJson(a.name())).append("\",");
                        json.append("\"percent\":").append(a.percent()).append(",");
                        json.append("\"phase\":\"").append(escJson(a.currentPhase())).append("\",");
                        json.append("\"completed\":").append(a.completed()).append(",");
                        json.append("\"failed\":").append(a.failed());
                        json.append("}");
                    }
                    json.append("]}");

                    emitter.send(SseEmitter.event().data(json.toString()));

                    if (snap.done()) break;
                    Thread.sleep(500);
                }
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        thread.setDaemon(true);
        thread.start();
        return emitter;
    }

    private String escJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }


    // ================================================================
    // CLEAR — POST /multi/clear
    // ================================================================

    /**
     * Deletes everything under resources/assessments/ — all uploaded files,
     * extracted submissions, and generated reports — giving a clean slate.
     *
     * Optionally accepts a specific assessment name to delete just one.
     * If no name is given, ALL assessments are deleted.
     */
    @PostMapping("/clear")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> clearAssessments(
            @RequestParam(value = "assessment", required = false) String assessment) {

        Map<String, Object> response = new HashMap<>();
        try {
            Path assessmentsRoot = Paths.get("resources", "assessments");

            if (assessment != null && !assessment.isBlank()) {
                // Delete one specific assessment folder
                clearDirectory(Paths.get("resources", "assessments", assessment));
                response.put("success", true);
                response.put("message", "Cleared assessment: " + assessment);
            } else {
                // Delete all assessment folders but keep the root
                Path root = Paths.get("resources", "assessments");
                if (Files.exists(root)) {
                    try (java.util.stream.Stream<Path> children = Files.list(root)) {
                        for (Path child : (Iterable<Path>) children::iterator) {
                            clearDirectory(child);
                        }
                    }
                }
                response.put("success", true);
                response.put("message", "All assessments cleared.");
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Clear failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
        return ResponseEntity.ok(response);
    }

    // ================================================================
    // DOWNLOAD — GET /multi/download
    // ================================================================

    /**
     * Downloads a report file for a specific assessment.
     *
     * @param assessment Sanitised assessment name (from /multi/status or /multi/grade)
     * @param file       "csv" or "excel"
     * @return File download response
     */
    @GetMapping("/download")
    public ResponseEntity<Resource> downloadReport(
            @RequestParam("assessment") String assessment,
            @RequestParam("file")       String file) {

        AssessmentPathConfig paths = new AssessmentPathConfig(assessment);
        Path reportFile;
        String filename;
        MediaType mediaType;

        if ("excel".equalsIgnoreCase(file)) {
            reportFile = paths.OUTPUT_REPORTS.resolve("IS442-Statistics.xlsx");
            filename   = assessment + "-Statistics.xlsx";
            mediaType  = MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        } else if ("scoresheet".equalsIgnoreCase(file)) {
            reportFile = paths.OUTPUT_REPORTS.resolve("IS442-ScoreSheet-Updated.xlsx");
            filename   = assessment + "-ScoreSheet-Updated.xlsx";
            mediaType  = MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        } else {
            // csv — ScoreSheetExporter now writes this alongside the xlsx
            reportFile = paths.OUTPUT_REPORTS.resolve("IS442-ScoreSheet-Updated.csv");
            filename   = assessment + "-ScoreSheet-Updated.csv";
            mediaType  = MediaType.parseMediaType("text/csv");
        }

        if (!Files.exists(reportFile)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(reportFile.toFile());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(mediaType)
                .body(resource);
    }

    // ================================================================
    // PRIVATE HELPERS
    // ================================================================

    /**
     * Scans resources/assessments/ for subdirectories and wraps each in a bundle.
     * If names is non-null and non-empty, only those names are included.
     */
    private List<AssessmentBundle> discoverBundles(List<String> filterNames) throws IOException {
        Path assessmentsRoot = Paths.get("resources", "assessments");
        if (!Files.exists(assessmentsRoot)) return Collections.emptyList();

        Set<String> filter = (filterNames != null && !filterNames.isEmpty())
                ? filterNames.stream()
                    .map(n -> AssessmentPathConfig.forName(n).getAssessmentName())
                    .collect(Collectors.toSet())
                : null;

        List<AssessmentBundle> bundles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(assessmentsRoot)) {
            for (Path dir : stream) {
                if (!Files.isDirectory(dir)) continue;
                String dirName = dir.getFileName().toString();
                if (filter != null && !filter.contains(dirName)) continue;

                AssessmentPathConfig config = new AssessmentPathConfig(dirName);
                // Use dirName as both the raw and sanitised name when discovering
                bundles.add(new AssessmentBundle(dirName, config));
            }
        }

        bundles.sort(Comparator.comparing(AssessmentBundle::getSanitisedName));
        return bundles;
    }

    private boolean hasFile(List<MultipartFile> files, int index) {
        return files != null && index < files.size()
                && files.get(index) != null
                && !files.get(index).isEmpty();
    }

    private void saveFile(MultipartFile file, Path destination) throws IOException {
        Files.createDirectories(destination.getParent());
        Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
    }

    private boolean hasZip(Path dir) {
        try {
            return Files.list(dir).anyMatch(p -> p.toString().toLowerCase().endsWith(".zip"));
        } catch (IOException e) { return false; }
    }

    private boolean hasJavaFiles(Path dir) {
        try {
            return Files.walk(dir).anyMatch(p -> p.toString().endsWith(".java"));
        } catch (IOException e) { return false; }
    }

    private void clearDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        Files.walk(dir)
             .sorted(Comparator.reverseOrder())
             .forEach(p -> { try { Files.delete(p); } catch (IOException e) {} });
    }

    private void extractZip(Path zipFile, Path destDir) throws IOException {
        Files.createDirectories(destDir);
        try (java.util.zip.ZipInputStream zis =
                new java.util.zip.ZipInputStream(Files.newInputStream(zipFile))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path target = destDir.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    private void flattenDirectory(Path rootDir) throws IOException {
        List<Path> allFiles = Files.walk(rootDir)
                .filter(Files::isRegularFile)
                .collect(Collectors.toList());

        for (Path file : allFiles) {
            String fileName = file.getFileName().toString();
            if (fileName.startsWith(".")) { Files.delete(file); continue; }
            Path target = rootDir.resolve(fileName);
            if (!file.getParent().equals(rootDir)) {
                Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        Files.walk(rootDir)
             .filter(Files::isDirectory)
             .filter(p -> !p.equals(rootDir))
             .sorted(Comparator.reverseOrder())
             .forEach(p -> { try { Files.delete(p); } catch (IOException e) {} });
    }
}