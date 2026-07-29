package com.example;

import com.example.parseRace.Athlete;

import java.util.ArrayList;
import java.util.List;

/**
 * Main orchestrator for bulk-scraping XC meet results from TFRRS.
 * 
 * Discovers XC meets, scrapes men's individual race results, fetches
 * athlete priors (with caching for efficiency), computes ratings,
 * and writes everything to a CSV file.
 * 
 * Delegates CSV writing to writeOutput, athlete document fetching to
 * scrapePriors, and prior rating calculation to priors — reusing all
 * existing project logic.
 *
 * Usage: java BulkRunner [maxPages]
 * maxPages - number of TFRRS search result pages to process (default: 1, each
 * page has ~30 meets)
 */
public class BulkRunner {

    private static final int ATHLETE_DELAY_MS = 1200; // 1.2s between athlete profile fetches
    private static final String OUTPUT_CSV = "bulk_results.csv";
    private static final String[] CSV_HEADER = { "Name", "Time", "Link", "Meet", "Date", "PriorRating" };

    public static void main(String[] args) {
        int maxPages = 1; // Default: 1 page = ~30 meets
        if (args.length > 0) {
            try {
                maxPages = Integer.parseInt(args[0]);
                if (maxPages < 1)
                    maxPages = 1;
            } catch (NumberFormatException e) {
                System.err.println("Invalid maxPages argument. Using default (1).");
            }
        }

        System.out.println("============================================================");
        System.out.println("  TFRRS XC Meet Bulk Scraper");
        System.out.println("  Processing up to " + maxPages + " page(s) of XC meets");
        System.out.println("============================================================");

        // Initialize components — reuse existing project classes
        MeetScraper meetScraper = new MeetScraper();
        RaceScraper raceScraper = new RaceScraper();
        AthleteCache cache = new AthleteCache();
        scrapePriors priorsScraper = new scrapePriors();
        priors priorsCalculator = new priors();
        writeOutput csvWriter = new writeOutput();

        // Step 1: Discover XC meets
        System.out.println("\n[Step 1] Discovering XC meets...");
        List<MeetScraper.MeetInfo> meets = meetScraper.scrapeXCMeets(maxPages);

        if (meets.isEmpty()) {
            System.out.println("No meets found. Exiting.");
            return;
        }

        // Stats tracking
        int totalMeetsProcessed = 0;
        int totalAthletesFound = 0;
        int totalAthletesSkipped = 0;
        int totalAthletesScraped = 0;
        int totalRowsWritten = 0;

        // Step 2 & 3: Process each meet
        System.out.println("\n[Step 2] Processing meets...\n");

        for (int i = 0; i < meets.size(); i++) {
            MeetScraper.MeetInfo meet = meets.get(i);
            System.out.println("─────────────────────────────────────────────────");
            System.out.printf("  Meet %d/%d: %s (%s)%n", i + 1, meets.size(), meet.name(), meet.date());
            System.out.println("  URL: " + meet.url());
            System.out.println("─────────────────────────────────────────────────");

            // Scrape men's race results
            List<Athlete> athletes = raceScraper.scrapeMensRace(meet.url());

            if (athletes.isEmpty()) {
                System.out.println("  → No men's results found, skipping.\n");
                totalMeetsProcessed++;
                continue;
            }

            totalAthletesFound += athletes.size();
            System.out.println("  → Found " + athletes.size() + " athletes");

            // Process each athlete
            List<String[]> csvRows = new ArrayList<>();

            for (int j = 0; j < athletes.size(); j++) {
                Athlete athlete = athletes.get(j);

                // Check cache first
                if (cache.contains(athlete.link())) {
                    totalAthletesSkipped++;
                    // Still write the row with basic info (no priors re-scrape needed)
                    csvRows.add(new String[] {
                            athlete.name(),
                            athlete.time(),
                            athlete.link(),
                            meet.name(),
                            meet.date(),
                            "cached" // Indicate this was from cache
                    });
                    continue;
                }

                // Need to scrape this athlete's priors
                if (athlete.link() == null || athlete.link().trim().isEmpty()) {
                    // No profile link available — write with limited data
                    csvRows.add(new String[] {
                            athlete.name(),
                            athlete.time(),
                            "",
                            meet.name(),
                            meet.date(),
                            "no-link"
                    });
                    continue;
                }

                // Rate limit between athlete fetches
                try {
                    Thread.sleep(ATHLETE_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("  Interrupted during athlete processing.");
                    break;
                }

                // Fetch athlete page and extract priors — reuse existing scrapePriors + priors
                try {
                    org.jsoup.nodes.Document athleteDoc = priorsScraper.getAthleteDocument(athlete.link());
                    String gender = priorsScraper.getGender(athleteDoc);
                    ArrayList<String> prs = priorsScraper.getPRs(athleteDoc);

                    double priorRating = priorsCalculator.getPriorRating(prs, gender);

                    // Add to cache
                    cache.add(athlete.link(), athlete.name());
                    totalAthletesScraped++;

                    String ratingStr = priorRating > 0 ? String.format("%.1f", priorRating) : "N/A";

                    csvRows.add(new String[] {
                            athlete.name(),
                            athlete.time(),
                            athlete.link(),
                            meet.name(),
                            meet.date(),
                            ratingStr
                    });

                    // Progress indicator every 10 athletes
                    if ((j + 1) % 10 == 0) {
                        System.out.printf("    Processed %d/%d athletes...%n", j + 1, athletes.size());
                    }

                } catch (Exception e) {
                    System.err.println("    Error processing athlete " + athlete.name() + ": " + e.getMessage());
                    csvRows.add(new String[] {
                            athlete.name(),
                            athlete.time(),
                            athlete.link(),
                            meet.name(),
                            meet.date(),
                            "error"
                    });
                }
            }

            // Write this meet's results to CSV — delegate to writeOutput
            int written = csvWriter.writeRows(OUTPUT_CSV, CSV_HEADER, csvRows);
            totalRowsWritten += written;

            totalMeetsProcessed++;
            System.out.printf("  → Wrote %d rows to CSV (skipped %d cached athletes)%n%n",
                    written, (int) csvRows.stream().filter(r -> r[5].equals("cached")).count());
        }

        // Print summary
        System.out.println("\n============================================================");
        System.out.println("  SCRAPING COMPLETE — SUMMARY");
        System.out.println("============================================================");
        System.out.printf("  Meets processed:    %d%n", totalMeetsProcessed);
        System.out.printf("  Athletes found:     %d%n", totalAthletesFound);
        System.out.printf("  Athletes scraped:   %d (new)%n", totalAthletesScraped);
        System.out.printf("  Athletes skipped:   %d (cached)%n", totalAthletesSkipped);
        System.out.printf("  CSV rows written:   %d%n", totalRowsWritten);
        System.out.printf("  Cache size:         %d total athletes%n", cache.size());
        System.out.printf("  Output file:        %s%n",
                writeOutput.getRelativePath(OUTPUT_CSV).toFile().getAbsolutePath());
        System.out.println("============================================================");
    }
}
