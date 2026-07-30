package com.example;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Read-only search over data.csv to check if an athlete (by profile link)
 * has already been processed. The CSV is written by writeOutput — this class
 * only reads it.
 */
public class AthleteCache {

    private final File cacheFile;

    public AthleteCache(Path cacheFilePath) {
        this.cacheFile = cacheFilePath.toFile();
    }


    public AthleteCache() {
        this(writeOutput.getRelativePath("data.csv"));
    }


    public boolean contains(String athleteLink) {
        if (athleteLink == null || athleteLink.trim().isEmpty()) {
            return false;
        }

        if (!cacheFile.exists()) {
            return false;
        }

        String normalizedInput = normalizeLink(athleteLink);

        try (CSVReader reader = new CSVReader(new FileReader(cacheFile))) {
            List<String[]> rows = reader.readAll();

            //in case headers were messed up
            int linkColIndex = -1; 
            if (!rows.isEmpty()) {
                String[] header = rows.get(0);
                for (int i = 0; i < header.length; i++) {
                    if (header[i].equalsIgnoreCase("Link")) {
                        linkColIndex = i;
                        break;
                    }
                }
            }
            if (linkColIndex == -1) {
                return false; 
            }

            for (int i = 1; i < rows.size(); i++) {
                String[] row = rows.get(i);
                if (row.length > linkColIndex && !row[linkColIndex].trim().isEmpty()) {
                    if (normalizeLink(row[linkColIndex]).equals(normalizedInput)) {
                        return true;
                    }
                }
            }
        } catch (IOException | CsvException e) {
            System.err.println("cache could not read data file: " + e.getMessage());
        }

        return false;
    }


    private String normalizeLink(String link) {
        String normalized = link.trim()
                .replaceAll("\\s+", "")
                .replaceAll("/$", "");

        if (normalized.startsWith("http://")) {
            normalized = "https://" + normalized.substring(7);
        }

        normalized = normalized.replace("://www.", "://");

        return normalized.toLowerCase();
    }
}
