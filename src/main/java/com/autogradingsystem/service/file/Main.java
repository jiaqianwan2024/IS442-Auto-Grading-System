import java.io.IOException;
import java.io.File;

public class Main {
    public static void main(String[] args) {
        // 1. Define your paths (Relative to your project folder)
        // Make sure you actually have a zip file named "test.zip" in your project folder!
        String zipFilePath = "test.zip"; 
        String destDirectory = "unzipped_output";

        System.out.println("--- Starting File Handling Process ---");

        try {
            // 2. Call the unzip function we built
            ZipFileProcessor.unzip(zipFilePath, destDirectory);
            
            System.out.println("Success! Files extracted to: " + destDirectory);
            
            // 3. Simple verification (Connecting to your whiteboard logic)
            File folder = new File(destDirectory);
            if (folder.exists() && folder.isDirectory()) {
                System.out.println("Files found inside: " + folder.list().length);
            }

        } catch (IOException e) {
            System.err.println("Error: Could not complete the unzip process.");
            System.err.println("Reason: " + e.getMessage());
            // This is where you would "Log Outlier" as per your flowchart
        }

        System.out.println("--- Process Finished ---");
    }
}