package com.example;

import com.example.RaceScraper.Athlete;

import java.util.ArrayList;
import java.util.List;


public class BulkRunner {

    private static final int ATHLETE_DELAY_MS = 1200; //rate limiting prevention
    private static final String OUTPUT_CSV = "bulk_results.csv";
    private static final String[] CSV_HEADER = { "Name", "Time", "Link", "Meet", "Date", "PriorRating" };

    public static void main(String[] args) {
        int maxPages = 1; //~30 meets, pages of meets most recently uploaded to tfrrs


        System.out.println("============================================================");
        System.out.println("  TFRRS XC Meet Bulk Scraper");
        System.out.println("  Processing up to " + maxPages + " page(s) of XC meets");
        System.out.println("============================================================");

        MeetScraper meetScraper = new MeetScraper();
        RaceScraper raceScraper = new RaceScraper();
        AthleteCache cache = new AthleteCache();
        scrapePriors priorsScraper = new scrapePriors();
        priors priorsCalculator = new priors();
        writeOutput csvWriter = new writeOutput();

        System.out.println("\n getting XC meets...");
        List<MeetScraper.MeetInfo> meets = meetScraper.scrapeXCMeets(maxPages);
        if (meets.isEmpty()) {
            System.out.println("No meets found.");
            return;
        }

        System.out.println("\n processing meets...\n");

        for (int i = 0; i < meets.size(); i++) {
            MeetScraper.MeetInfo meet = meets.get(i);
            List<Athlete> athletes = raceScraper.scrapeMensRace(meet.url());
            //if gender/results not present
            if (athletes.isEmpty()) {
                continue;
            }

            //athlete processing 
            List<String[]> csvRows = new ArrayList<>();
            for (int j = 0; j < athletes.size(); j++) {
                Athlete athlete = athletes.get(j);
                //check if athlete already exists
                if (cache.contains(athlete.link())) {
                    // Still write the row with basic info CHANGE TO COMPARE RATING AND REWRITE RATING 
                    csvRows.add(new String[] {
                            athlete.name(),
                            athlete.time(),
                            athlete.link(),
                            meet.name(),
                            meet.date(),
                            "cached" 
                    });
                    continue;
                }

                //athlete profile doesnt exist
                if (athlete.link() == null || athlete.link().trim().isEmpty()) {
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

                // rate limit between athletes
                try {
                    Thread.sleep(ATHLETE_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.out.println("Error");
                    break;
                }

                try {
                    org.jsoup.nodes.Document athleteDoc = priorsScraper.getAthleteDocument(athlete.link());
                    String gender = priorsScraper.getGender(athleteDoc);
                    ArrayList<String> prs = priorsScraper.getPRs(athleteDoc);

                    double priorRating = priorsCalculator.getPriorRating(prs, gender);


                    String ratingStr = priorRating > 0 ? String.format("%.1f", priorRating) : "N/A";

                    csvRows.add(new String[] {
                            athlete.name(),
                            athlete.time(),
                            athlete.link(),
                            meet.name(),
                            meet.date(),
                            ratingStr
                    });


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

            //write out 
            csvWriter.writeRows(OUTPUT_CSV, CSV_HEADER, csvRows);


        }

        // Print summary
        System.out.println("\n============================================================");
        System.out.println("  SCRAPING COMPLETE — SUMMARY");
        System.out.println("============================================================");
        System.out.printf("  Output file:        %s%n",
                writeOutput.getRelativePath(OUTPUT_CSV).toFile().getAbsolutePath());

        System.out.println("============================================================");
    }
}
