package com.autogradingsystem.service.file;

import java.io.*;
import java.util.*;

/**
 * Reads the official student list from the scoresheet CSV.
 * This helps detect if an unzipped folder belongs to a real student.
 */
public class ScoreSheetReader {
    
    // We use a HashSet for fast "contains" checks
    private Set<String> validUsernames = new HashSet<>();

    /**
     * Reads the CSV and stores usernames without the '#' prefix.
     * @param csvPath Path to the IS442-ScoreSheet.csv
     */
    public void loadValidStudents(String csvPath) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            String line;
            
            // Skip the header row (OrgDefinedId, Username, etc.)
            br.readLine(); 
            
            while ((line = br.readLine()) != null) {
                // Split by comma to get individual columns
                String[] columns = line.split(",");
                
                // Check if column exists (Username is in the 2nd column)
                if (columns.length > 1) {
                    String rawUser = columns[1];
                    
                    // Clean the data: remove '#' and any extra spaces
                    String cleanUser = rawUser.replace("#", "").trim();
                    validUsernames.add(cleanUser);
                }
            }
        }
    }

    /**
     * Returns true if the username exists in the official list.
     */
    public boolean isValid(String username) {
        return validUsernames.contains(username);
    }
}