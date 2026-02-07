package com.autogradingsystem.service.file;

import java.io.*;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * <p>This method unzips a single file to target file path
 * 
 * </p>
 * @param zipFilePath The path to the target zipped file, expressed as a String
 * @param destDirectory The path to the directory where the unzipped file will be placed, as a String
 * @throws Error if zip file does not exist
 */
public class ZipFileProcessor{
    public static void unzip (String zipFilePath, String destDirectory) throws IOException{
        File destDir = new File(destDirectory); //Java object pointing to a file path
        if (!destDir.exists()) { 
            destDir.mkdirs(); // Create the folder if it doesn't exist
        }

        try (ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipFilePath))) {
            ZipEntry entry = zipIn.getNextEntry(); //Read the next entry in the file 

            while (entry != null){ //Iterate through each file in the zip file
                Path studentFilePath = Paths.get(destDirectory, entry.getName()); // Paths.get makes a new file inside the file path specified at function call

                if (!entry.isDirectory()){
                    Files.createDirectories(studentFilePath.getParent()); //Check for parent directory, create if it does not exist
                    //Since the parents on the filepath may not exist, this is impt
                    
                    Files.copy(zipIn, studentFilePath, StandardCopyOption.REPLACE_EXISTING);
                    //Copy code in the file into the studentFilePath
                } else {
                    Files.createDirectories(studentFilePath);
                }
                zipIn.closeEntry(); 
                entry = zipIn.getNextEntry(); //Move onto the next file inside the zipfile


            }

            
    }
    }
}
