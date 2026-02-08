package com.autogradingsystem.service.file;

import java.io.*;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.autogradingsystem.service.validation.ScoreSheetReader;

public class SubmissionUnzipper {

    /**
     * Simple data structure to hold the results of our "Detective Work".
     * Used to generate the final non-technical summary report.
     */
    static class SubmissionReport {
        String filename;
        String status;
        String resolvedId;

        SubmissionReport(String filename, String status, String resolvedId) {
            this.filename = filename;
            this.status = status;
            this.resolvedId = resolvedId;
        }
    }

    public static void main(String[] args) {
        // Resource paths
        String csvPath = "config/IS442-ScoreSheet.csv";
        String masterZip = "data/input/submissions/student-submission.zip";
        
        
        // Working directories
        String tempRoot = "temp_individual_zips"; // Need a place to put these student zips so this java file can loop through them one by one
        String finalFolder = "extracted_student_work"; // This holds the final verified folders that are ready to be graded

        ScoreSheetReader reader = new ScoreSheetReader();
        List<SubmissionReport> summary = new ArrayList<>();
        
        try {
            // STEP 1: Load the official class list
            reader.loadValidStudents(Paths.get(csvPath));

            // STEP 2: Extract the master zip containing all students
            ZipFileProcessor.unzip(masterZip, tempRoot);

            // STEP 3: Iterate through the individual student files
            File innerFolder = new File(tempRoot + "/student-submission");
            File[] studentZips = innerFolder.listFiles();

            if (studentZips != null) {
                for (File zip : studentZips) {
                    if (zip.getName().endsWith(".zip")) {
                        // The processStudent method now handles the 3-layer check
                        summary.add(processStudent(zip, finalFolder, reader));
                    }
                }
            }

            // STEP 4: Print the final aligned report
            printSummaryTable(summary);

        } catch (IOException e) {
            System.err.println("Process Error: " + e.getMessage());
        } finally {
            // FINAL CLEANUP: Ensure temporary folders are deleted even if errors occur
            deleteDirectory(new File("temp_check"));
            deleteDirectory(new File(tempRoot));
        }
    }

    private static SubmissionReport processStudent(File zip, String dest, ScoreSheetReader reader) throws IOException {
        String originalName = zip.getName();
        // Remove .zip and the "2023-2024-" prefix
        String cleanZipName = originalName.replace(".zip", "").replace("2023-2024-", "");
        
        // --- LAYER 1: VALIDATE FILENAME ---
        if (reader.isValid(cleanZipName)) {
            ZipFileProcessor.unzip(zip.getAbsolutePath(), dest + "/" + cleanZipName);
            return new SubmissionReport(originalName, "MATCHED (Filename correct)", cleanZipName);
        } 
        
        // --- LAYER 2: VALIDATE INTERNAL FOLDER ---
        // If filename fails, unzip to temp_check to look inside
        String tempPath = "temp_check/" + cleanZipName;
        ZipFileProcessor.unzip(zip.getAbsolutePath(), tempPath);
        File tempFolder = new File(tempPath);
        File[] contents = tempFolder.listFiles();

        if (contents != null) {
            for (File f : contents) {
                if (f.isDirectory() && reader.isValid(f.getName())) {
                    String internalName = f.getName();
                    // Move the valid folder to the final destination
                    f.renameTo(new File(dest + "/" + internalName));
                    return new SubmissionReport(originalName, "RECOVERED (Student username typo found; verified via internal folder)", internalName);
                }
            }
        }

        // --- LAYER 3: DEEP SCAN SOURCE CODE ---
        // If Layer 1 and 2 both fail, we open the .java files to find "Email ID:"
        String discoveredId = scanJavaFilesForEmail(tempFolder, reader);
        
        if (discoveredId != null) {
            // If ID is found in code, move the first folder found to the verified name
            if (contents != null && contents.length > 0) {
                contents[0].renameTo(new File(dest + "/" + discoveredId));
                return new SubmissionReport(originalName, "RECOVERED (Student username typo found; verified via Java file comments)", discoveredId);
            }
        }

        // --- FINAL ANOMALY ---
        return new SubmissionReport(originalName, "UNRECOGNIZED (No valid ID found in filename, folder, or source code)", "N/A");
    }

    /**
     * Detective Method: Recursively searches for any .java file in the student's submission
     * and reads the header comments to find the "Email ID:".
     */
    private static String scanJavaFilesForEmail(File folder, ScoreSheetReader reader) {
        List<File> allFiles = new ArrayList<>();
        getFilesRecursive(folder, allFiles);
        
        for (File f : allFiles) {
            if (f.getName().endsWith(".java")) {
                try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (line.contains("Email ID:")) {
                            // Split the line into parts based on the colon
                            String[] parts = line.split("Email ID:");
                            
                            // SAFETY CHECK: Only proceed if there is actually text after the colon
                            if (parts.length > 1) {
                                String extracted = parts[1].trim();
                                // Check if this extracted ID exists in our official class list
                                if (reader.isValid(extracted)) {
                                    return extracted;
                                }
                            }
                        }
                    }
                } catch (IOException ignored) {} 
            }
        }
        return null; 
    }

    // Helper to find all files inside sub-folders like Q1, Q2, etc.
    private static void getFilesRecursive(File dir, List<File> fileList) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) getFilesRecursive(f, fileList);
                else fileList.add(f);
            }
        }
    }

    private static void printSummaryTable(List<SubmissionReport> summary) {
        String tableLine = "=".repeat(156);
        System.out.println("\n" + tableLine);
        System.out.println("                                               FINAL SUBMISSION VALIDATION REPORT");
        System.out.println(tableLine);
        System.out.printf("%-35s | %-100s | %-15s%n", "SUBMITTED FILENAME", "VERIFICATION DETAILS", "VERIFIED ID");
        System.out.println("-".repeat(156));

        for (SubmissionReport r : summary) {
            System.out.printf("%-35s | %-100s | %-15s%n", r.filename, r.status, r.resolvedId);
        }
        System.out.println(tableLine);
    }

    private static void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) deleteDirectory(f);
        dir.delete();
    }
}