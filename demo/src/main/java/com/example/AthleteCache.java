package com.example;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvException;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * CSV-backed cache of athlete profile URLs that have already been scraped.
 * Prevents redundant network requests for athletes we've already processed,
 * both within a single run and across multiple runs.
 */
public class AthleteCache {

    private final Set<String> cachedLinks;
    private final File cacheFile;

    /**
     * Creates or loads an athlete cache from the given file path.
     * The CSV has columns: Link, Name
     */
    public AthleteCache(Path cacheFilePath) {
        this.cacheFile = cacheFilePath.toFile();
        this.cachedLinks = new HashSet<>();
        loadCache();
    }

    /**
     * Creates an AthleteCache using the default location (next to JAR/classes).
     */
    public AthleteCache() {
        this(writeOutput.getRelativePath("scraped_athletes.csv"));
    }

    /**
     * Loads existing cache entries from the CSV file.
     */
    private void loadCache() {
        if (!cacheFile.exists()) {
            return;
        }

        try (CSVReader reader = new CSVReader(new FileReader(cacheFile))) {
            List<String[]> rows = reader.readAll();
            for (int i = 0; i < rows.size(); i++) {
                String[] row = rows.get(i);
                // Skip header row
                if (i == 0 && row.length > 0 && row[0].equalsIgnoreCase("Link")) {
                    continue;
                }
                if (row.length > 0 && !row[0].trim().isEmpty()) {
                    cachedLinks.add(normalizeLink(row[0]));
                }
            }
            System.out.println("[AthleteCache] Loaded " + cachedLinks.size() + " cached athletes from "
                    + cacheFile.getAbsolutePath());
        } catch (IOException | CsvException e) {
            System.err.println("[AthleteCache] Warning: could not read cache file: " + e.getMessage());
        }
    }

    /**
     * Checks if an athlete link is already in the cache.
     */
    public boolean contains(String athleteLink) {
        if (athleteLink == null || athleteLink.trim().isEmpty()) {
            return false;
        }
        return cachedLinks.contains(normalizeLink(athleteLink));
    }

    /**
     * Adds an athlete to the cache and appends to the CSV file.
     */
    public void add(String athleteLink, String name) {
        if (athleteLink == null || athleteLink.trim().isEmpty()) {
            return;
        }

        String normalized = normalizeLink(athleteLink);
        if (cachedLinks.contains(normalized)) {
            return; // Already cached
        }

        cachedLinks.add(normalized);

        // Append to CSV
        boolean newFile = !cacheFile.exists() || cacheFile.length() == 0;
        try (CSVWriter writer = new CSVWriter(new FileWriter(cacheFile, true))) {
            if (newFile) {
                writer.writeNext(new String[] { "Link", "Name" });
            }
            writer.writeNext(new String[] { athleteLink, name != null ? name : "" });
        } catch (IOException e) {
            System.err.println("[AthleteCache] Warning: could not write to cache file: " + e.getMessage());
        }
    }

    /**
     * Returns the number of cached athletes.
     */
    public int size() {
        return cachedLinks.size();
    }

    /**
     * Normalizes a link for consistent cache lookups.
     * Strips trailing slashes, whitespace, and normalizes protocol.
     */
    private String normalizeLink(String link) {
        String normalized = link.trim()
                .replaceAll("\\s+", "")
                .replaceAll("/$", "");

        // Normalize to https
        if (normalized.startsWith("http://")) {
            normalized = "https://" + normalized.substring(7);
        }

        // Remove www. prefix for consistency
        normalized = normalized.replace("://www.", "://");

        return normalized.toLowerCase();
    }
}
