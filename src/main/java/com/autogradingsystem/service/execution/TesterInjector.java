package com.autogradingsystem.service.execution;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class TesterInjector {

    /**
     * Locates a Tester file in the project resources and copies it to the student's folder.
     * * @param testerName        The name of the file to copy (e.g., "Q1aTester.java")
     * @param destinationFolder The path to the student's submission folder (e.g., /tmp/mock_students/student1/Q1)
     * @throws IOException      If the file cannot be found or copied.
     */
    public void copyTester(String testerName, java.nio.file.Path destinationFolder) throws IOException {
        String resourcePath = "testers/" + testerName;
        
        // -----------------------------------------------------
        // STRATEGY 1: CLASSPATH LOADER (The "Proper" Way)
        // -----------------------------------------------------
        // This attempts to load the file from the compiled project. 
        // This is how it works when you package the app as a JAR file.
        InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);

        // -----------------------------------------------------
        // STRATEGY 2: LOCAL FILESYSTEM (The "Dev" Way)
        // -----------------------------------------------------
        // When running simple "java Main.java" commands or in some IDEs, 
        // the Classpath might not be set up to see "src/main/resources".
        // If Strategy 1 fails (is == null), we look manually on the disk.
        if (is == null) {
            System.out.println("[Injector] Resource not found in classpath. Checking local file system...");
            
            // HARDCODED PATH ALERT: This looks specifically in your source folder.
            File localFile = new File("src/main/resources/testers/" + testerName);
            
            if (localFile.exists()) {
                is = new FileInputStream(localFile);
            }
        }

        // -----------------------------------------------------
        // ERROR HANDLING
        // -----------------------------------------------------
        // If both strategies fail, we cannot grade. Stop everything.
        if (is == null) {
            throw new IOException("Tester file not found: " + resourcePath + 
                "\n(Checked both Classpath and 'src/main/resources/testers/')");
        }

        // -----------------------------------------------------
        // FILE COPYING
        // -----------------------------------------------------
        // 1. Make sure the student folder actually exists. 
        // If the student submitted a file but deleted the folder, we recreate it.
        if (!Files.exists(destinationFolder)) {
            Files.createDirectories(destinationFolder);
        }

        // 2. Define where the file is going
        File targetFile = new File(destinationFolder.toFile(), testerName);
        
        // 3. Perform the Copy
        // StandardCopyOption.REPLACE_EXISTING is crucial. 
        // It ensures that if we run the grader twice, we overwrite the old tester 
        // instead of crashing with a "FileAlreadyExists" error.
        Files.copy(is, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        
        System.out.println("[Injector] Copied " + testerName + " to " + destinationFolder);
        
        // Always close streams to prevent memory leaks!
        is.close();
    }
}