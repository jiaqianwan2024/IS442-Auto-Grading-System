package com.autogradingsystem.web.controller;

import com.autogradingsystem.web.service.GradingService;
import com.autogradingsystem.web.service.GradingService.GradingReport;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

@Controller
public class GradingController {

    @Autowired
    private GradingService gradingService;

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @PostMapping("/upload")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> handleUpload(
            @RequestParam(value = "submission", required = false) MultipartFile submissionZip,
            @RequestParam(value = "template", required = false) MultipartFile templateZip,
            @RequestParam(value = "scoresheet", required = false) MultipartFile scoreSheetCsv,
            @RequestParam(value = "testers", required = false) MultipartFile testersZip,
            @RequestParam(value = "examPdf", required = false) MultipartFile examPdfFile) {

        Map<String, Object> response = new HashMap<>();
        Map<String, String> uploaded = new HashMap<>();
        StringBuilder messages = new StringBuilder();

        try {
            if (submissionZip != null && !submissionZip.isEmpty()) {
                Path destination = Paths.get("resources/input/submissions/student-submission.zip");
                Files.createDirectories(destination.getParent());
                Files.copy(submissionZip.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
                uploaded.put("submission", submissionZip.getOriginalFilename());
                messages.append("✓ Student submissions uploaded. ");
            }

            if (templateZip != null && !templateZip.isEmpty()) {
                Path destination = Paths.get("resources/input/template/RenameToYourUsername.zip");
                Files.createDirectories(destination.getParent());
                Files.copy(templateZip.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
                uploaded.put("template", templateZip.getOriginalFilename());
                messages.append("✓ Template uploaded. ");
            }

            if (scoreSheetCsv != null && !scoreSheetCsv.isEmpty()) {
                Path destination = Paths.get("config/IS442-ScoreSheet.csv");
                Files.createDirectories(destination.getParent());
                Files.copy(scoreSheetCsv.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
                uploaded.put("scoresheet", scoreSheetCsv.getOriginalFilename());
                messages.append("✓ Score sheet uploaded. ");
            }

            if (testersZip != null && !testersZip.isEmpty()) {
                Path tempZip = Paths.get("resources/input/testers-temp.zip");
                Files.createDirectories(tempZip.getParent());
                Files.copy(testersZip.getInputStream(), tempZip, StandardCopyOption.REPLACE_EXISTING);
                Path testersDir = Paths.get("resources/input/testers");
                if (Files.exists(testersDir)) {
                    Files.walk(testersDir).sorted((a, b) -> -a.compareTo(b))
                         .forEach(path -> { try { Files.delete(path); } catch (IOException e) {} });
                }
                Files.createDirectories(testersDir);
                extractZip(tempZip, testersDir);
                flattenDirectory(testersDir);
                Files.deleteIfExists(tempZip);
                uploaded.put("testers", testersZip.getOriginalFilename());
                messages.append("✓ Test cases uploaded and extracted. ");
            }

            // Exam paper PDF — save to resources/input/exam/
            if (examPdfFile != null && !examPdfFile.isEmpty()) {
                Path examDir = Paths.get("resources/input/exam");
                Files.createDirectories(examDir);
                // Remove any previous PDF so there is always exactly one
                try (var stream = Files.list(examDir)) {
                    stream.filter(p -> p.toString().toLowerCase().endsWith(".pdf"))
                          .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
                }
                String pdfName = Paths.get(examPdfFile.getOriginalFilename()).getFileName().toString();
                Path destination = examDir.resolve(pdfName);
                Files.copy(examPdfFile.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);
                uploaded.put("examPdf", pdfName);
                messages.append("✓ Exam paper PDF uploaded. ");
                System.out.println("📑 Exam PDF saved: " + destination.toAbsolutePath());
            }

            if (uploaded.isEmpty()) {
                response.put("success", false);
                response.put("message", "No files were uploaded");
                return ResponseEntity.badRequest().body(response);
            }

            response.put("success", true);
            response.put("message", messages.toString());
            response.put("uploaded", uploaded);
            return ResponseEntity.ok(response);

        } catch (IOException e) {
            response.put("success", false);
            response.put("message", "Upload failed: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/check-uploads")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkUploads() {
        Map<String, Object> response = new HashMap<>();
        Map<String, Boolean> exists = new HashMap<>();

        boolean hasSubmission = Files.exists(Paths.get("resources/input/submissions/student-submission.zip"));
        boolean hasTemplate   = Files.exists(Paths.get("resources/input/template/RenameToYourUsername.zip"));
        boolean hasScoresheet = Files.exists(Paths.get("config/IS442-ScoreSheet.csv"));
        boolean hasTesters    = Files.exists(Paths.get("resources/input/testers")) &&
                                hasJavaFiles(Paths.get("resources/input/testers"));
        boolean hasExamPdf    = hasExamPdf(Paths.get("resources/input/exam"));

        exists.put("submission", hasSubmission);
        exists.put("template",   hasTemplate);
        exists.put("scoresheet", hasScoresheet);
        exists.put("testers",    hasTesters);
        exists.put("examPdf",    hasExamPdf);

        int uploadedCount = (hasSubmission ? 1 : 0) + (hasTemplate ? 1 : 0) +
                            (hasScoresheet ? 1 : 0) + (hasTesters ? 1 : 0) +
                            (hasExamPdf ? 1 : 0);

        response.put("exists",        exists);
        response.put("uploadedCount", uploadedCount);
        // ready = all 4 required files present (testers are auto-generated; examPdf required)
        response.put("ready", hasSubmission && hasTemplate && hasScoresheet && hasExamPdf);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/grade")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> runGrading() {
        GradingReport report = gradingService.runFullPipeline();

        Map<String, Object> response = new HashMap<>();
        response.put("success",      report.isSuccess());
        response.put("studentCount", report.getStudentCount());
        response.put("logs",         report.getLogs());

        if (report.getResults() != null && !report.getResults().isEmpty()) {
            response.put("taskCount", report.getResults().size());
            double totalScore = 0, totalMax = 0;
            int perfectCount = 0;
            for (var result : report.getResults()) {
                totalScore += result.getScore();
                totalMax   += result.getMaxScore();
                if (result.isPerfect()) perfectCount++;
            }
            double averageScore = totalMax > 0 ? (totalScore / totalMax) * 100 : 0;
            double successRate  = (perfectCount * 100.0 / report.getResults().size());
            response.put("averageScore", averageScore);
            response.put("successRate",  successRate);
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/download/csv")
    public ResponseEntity<Resource> downloadCsv() {
        Path file = Paths.get("resources/output/reports/IS442-ScoreSheet-Updated.xlsx");
        if (!Files.exists(file)) return ResponseEntity.notFound().build();
        Resource resource = new FileSystemResource(file.toFile());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=IS442-ScoreSheet-Updated.xlsx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(resource);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void extractZip(Path zipFile, Path destDir) throws IOException {
        try (java.util.zip.ZipInputStream zis =
                new java.util.zip.ZipInputStream(Files.newInputStream(zipFile))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path targetPath = destDir.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(zis, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    private boolean hasJavaFiles(Path dir) {
        try {
            return Files.walk(dir).anyMatch(path -> path.toString().endsWith(".java"));
        } catch (IOException e) { return false; }
    }

    private boolean hasExamPdf(Path dir) {
        if (!Files.exists(dir)) return false;
        try (var stream = Files.list(dir)) {
            return stream.anyMatch(p -> p.toString().toLowerCase().endsWith(".pdf"));
        } catch (IOException e) { return false; }
    }

    private void flattenDirectory(Path rootDir) throws IOException {
        java.util.List<Path> allFiles = Files.walk(rootDir)
                .filter(Files::isRegularFile)
                .collect(java.util.stream.Collectors.toList());
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
             .sorted((a, b) -> b.compareTo(a))
             .forEach(p -> { try { Files.delete(p); } catch (IOException e) {} });
    }
}