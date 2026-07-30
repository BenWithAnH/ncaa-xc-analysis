package com.example;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Scrapes individual meet pages to extract men's XC race results.
 * Finds the men's individual results table and extracts athlete name, time, and
 * profile link.
 *
 * Also provides general-purpose table-parsing utilities (getColumn,
 * getAthleteLink) that were formerly in parseRace.
 */
public class RaceScraper {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    private static final int TIMEOUT_MS = 15000;
    private static final int MEET_DELAY_MS = 2000; // 2s delay before fetching

    // --- Athlete record (moved from parseRace) ---

    public record Athlete(String name, String time, String link) {
    }

    // --- Table-parsing state and helpers (moved from parseRace) ---

    private int nameIndex = -1;

    /**
     * Extracts all values from a named column in an HTML table.
     * The first row is assumed to be the header row.
     */
    public List<String> getColumn(Element el, String rowName) {
        List<String> columnValues = new ArrayList<>();
        Element headerRow = el.selectFirst("tr");
        int colIndex = -1;
        if (headerRow != null) {
            Elements headers = headerRow.select("th, td");
            for (int i = 0; i < headers.size(); i++) {
                if (headers.get(i).text().equalsIgnoreCase(rowName)) {
                    colIndex = i; // Store 0-based index
                    if (rowName.equals("Name"))
                        nameIndex = i;
                    break;
                }
            }
        }
        if (colIndex != -1) {
            Elements rows = el.select("tr");
            for (int i = 1; i < rows.size(); i++) {
                Element row = rows.get(i);
                Elements cells = row.select("th, td");
                // Ensure the row has enough columns (handles empty or irregular rows)
                if (cells.size() > colIndex) {
                    columnValues.add(cells.get(colIndex).text());
                }
            }
        }
        return columnValues;
    }

    /**
     * Extracts athlete profile links from the Name column of an HTML table.
     * Must be called after getColumn(el, "Name") so that nameIndex is set.
     */
    public List<String> getAthleteLink(Element el) {
        List<String> links = new ArrayList<>();
        if (nameIndex != -1) {
            Elements rows = el.select("tr");
            for (int i = 1; i < rows.size(); i++) {
                Element row = rows.get(i);
                Elements cells = row.select("th, td");
                if (cells.size() > nameIndex) {
                    Element nameCell = cells.get(nameIndex);
                    Element link = nameCell.selectFirst("a");
                    if (link != null) {
                        String url = link.absUrl("href").replaceAll("\\s+", "");
                        links.add(url);
                    } else {
                        links.add(""); // Keep alignment
                    }
                }
            }
        }
        return links;
    }

    // --- Simple race scraper (moved from parseRace.getCAF) ---

    /**
     * Fetches a race results page and extracts all athletes from the first
     * table containing a "Year" column. This is a simpler alternative to
     * scrapeMensRace() that doesn't filter for men's CC events specifically.
     *
     * @param raceLink full URL to the race results page
     * @return list of Athlete records, or null on failure
     */
    public List<Athlete> scrapeRace(String raceLink) {
        try {
            Document doc = Jsoup.connect(raceLink)
                    .get();

            Element el = doc.selectFirst("table:contains(Year)");
            List<String> names = getColumn(el, "Name");
            List<String> times = getColumn(el, "Time");
            List<String> links = getAthleteLink(el);
            List<Athlete> athletes = new ArrayList<>();
            int limit = Math.min(names.size(), Math.min(times.size(), links.size()));
            for (int i = 0; i < limit; i++) {
                athletes.add(new Athlete(names.get(i), times.get(i), links.get(i)));
            }

            return athletes;

        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    // --- Men's XC-specific scraper ---

    /**
     * Scrapes the men's individual race results from a TFRRS XC meet page.
     *
     * @param meetUrl full URL to the meet results page
     * @return list of Athlete records with name, time, and profile link; empty list
     *         on failure
     */
    public List<Athlete> scrapeMensRace(String meetUrl) {
        List<Athlete> athletes = new ArrayList<>();

        try {
            // Rate limit
            Thread.sleep(MEET_DELAY_MS);

            Document doc = Jsoup.connect(meetUrl)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .maxBodySize(0) // No limit on body size — meet pages can be very large
                    .get();

            // Find all event section headings — they look like:
            // <h3>Men 10k Run CC Individual Results (10k)</h3>
            // or <h3>Men 8k Run CC Individual Results (8k)</h3>
            // We need to find the "Individual Results" heading for a Men's CC event.

            Elements headings = doc.select("h3");
            Element mensIndividualTable = null;
            String eventName = "";

            for (Element h3 : headings) {
                String text = h3.text().trim();
                // Match headings like "Men 8k Run CC Individual Results" or "Men 10k Run CC
                // Individual Results"
                if (text.toLowerCase().contains("men")
                        && !text.toLowerCase().contains("women")
                        && text.toLowerCase().contains("cc")
                        && text.toLowerCase().contains("individual")) {

                    eventName = text;
                    // The results table immediately follows this heading's parent div structure
                    // Walk up to find the containing div, then find the next table
                    Element container = h3.parent();
                    if (container != null) {
                        // The table is a sibling or descendant of the heading's container
                        Element tableParent = container.parent();
                        if (tableParent != null) {
                            mensIndividualTable = tableParent.selectFirst("table");
                        }
                    }

                    // Alternative: try finding the table as a next sibling of the heading's wrapper
                    if (mensIndividualTable == null) {
                        Element current = h3;
                        while (current != null) {
                            Element next = current.nextElementSibling();
                            if (next != null && next.tagName().equals("table")) {
                                mensIndividualTable = next;
                                break;
                            }
                            if (next != null && next.selectFirst("table") != null) {
                                mensIndividualTable = next.selectFirst("table");
                                break;
                            }
                            // Try going up one level
                            current = current.parent();
                            if (current != null && current.tagName().equals("div")) {
                                Element nextDiv = current.nextElementSibling();
                                if (nextDiv != null) {
                                    Element tbl = nextDiv.selectFirst("table");
                                    if (tbl != null) {
                                        mensIndividualTable = tbl;
                                        break;
                                    }
                                }
                            }
                            break;
                        }
                    }

                    if (mensIndividualTable != null) {
                        break;
                    }
                }
            }

            // Fallback: if we couldn't find the men's individual results via heading,
            // look for any table that follows a "Men" event heading in the event structure
            if (mensIndividualTable == null) {
                mensIndividualTable = findMensTableFallback(doc);
            }

            if (mensIndividualTable == null) {
                System.out.println("[RaceScraper] No men's individual results table found at: " + meetUrl);
                return athletes;
            }

            // Extract athletes from the table using local helpers
            athletes = extractAthletesFromTable(mensIndividualTable);

            if (!eventName.isEmpty()) {
                System.out.println("[RaceScraper] Found " + athletes.size() + " athletes in: " + eventName);
            }

        } catch (IOException e) {
            System.err.println("[RaceScraper] Error fetching meet page: " + meetUrl + " - " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[RaceScraper] Interrupted while scraping: " + meetUrl);
        }

        return athletes;
    }

    /**
     * Fallback method: find the men's individual results table by looking for
     * tables that contain athlete links and are associated with a "Men" event.
     */
    private Element findMensTableFallback(Document doc) {
        // Strategy: find all custom-table-title divs that mention "Men" and
        // "Individual"
        Elements titleDivs = doc.select("div.custom-table-title");
        for (Element titleDiv : titleDivs) {
            String titleText = titleDiv.text().toLowerCase();
            if (titleText.contains("men") && !titleText.contains("women") && titleText.contains("individual")) {
                // The table should be a sibling of this title div
                Element table = null;
                // Walk siblings after the title div
                Element sibling = titleDiv.nextElementSibling();
                while (sibling != null) {
                    if (sibling.tagName().equals("table")) {
                        table = sibling;
                        break;
                    }
                    table = sibling.selectFirst("table");
                    if (table != null)
                        break;
                    sibling = sibling.nextElementSibling();
                }
                if (table != null) {
                    return table;
                }
            }
        }

        // Second fallback: find the last large table with athlete links on the page
        // (Men's events typically come after Women's events on TFRRS)
        Elements allTables = doc.select("table.tablesaw-xc");
        Element lastAthleteTable = null;
        for (Element table : allTables) {
            // Check if this table has athlete links (individual results, not team results)
            Elements athleteLinks = table.select("a[href*=/athletes/]");
            if (!athleteLinks.isEmpty()) {
                // Check if any team link is for a men's team (URL contains _m_ or college_m)
                Elements teamLinks = table.select("a[href*=_college_m_], a[href*=_m_]");
                if (!teamLinks.isEmpty()) {
                    lastAthleteTable = table;
                }
            }
        }

        return lastAthleteTable;
    }

    /**
     * Extracts athletes from a results table using getColumn() and
     * getAthleteLink().
     */
    private List<Athlete> extractAthletesFromTable(Element table) {
        // Use local column extraction methods
        List<String> names = getColumn(table, "Name");
        List<String> times = getColumn(table, "Time");
        List<String> links = getAthleteLink(table);

        List<Athlete> athletes = new ArrayList<>();
        int limit = Math.min(names.size(), Math.min(times.size(), links.size()));

        for (int i = 0; i < limit; i++) {
            String name = names.get(i).trim();
            String time = times.get(i).trim();
            String link = links.get(i).trim();

            // Skip empty/invalid entries
            if (name.isEmpty() || time.isEmpty()) {
                continue;
            }

            athletes.add(new Athlete(name, time, link));
        }

        return athletes;
    }

    /**
     * Extracts the race distance in meters from the event name on the meet page.
     * E.g., "Men 8k Run CC" → 8000.0, "Men 10k Run CC" → 10000.0
     *
     * @param meetUrl the meet URL to check
     * @return distance in meters (defaults to 8000.0 if not determinable)
     */
    public double detectRaceDistance(String meetUrl) {
        // Reuse scrapePriors' document-fetching pattern
        scrapePriors scraper = new scrapePriors();
        Document doc = scraper.getAthleteDocument(meetUrl);
        if (doc == null) {
            return 8000.0;
        }
        return detectRaceDistanceFromDoc(doc);
    }

    /**
     * Extracts race distance from a pre-fetched document.
     */
    public double detectRaceDistanceFromDoc(Document doc) {
        Elements headings = doc.select("h3");
        for (Element h3 : headings) {
            String text = h3.text().trim().toLowerCase();
            if (text.contains("men") && !text.contains("women") && text.contains("cc")) {
                // Look for patterns like "8k", "10k", "5k", "6k"
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)k")
                        .matcher(text);
                if (m.find()) {
                    return Double.parseDouble(m.group(1)) * 1000.0;
                }
            }
        }

        // Fallback: also check event list links
        Elements eventLinks = doc.select("ol.events-list a, select#quick-links-select option");
        for (Element el : eventLinks) {
            String text = el.text().trim().toLowerCase();
            if (text.contains("men") && !text.contains("women") && text.contains("cc")) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)k")
                        .matcher(text);
                if (m.find()) {
                    return Double.parseDouble(m.group(1)) * 1000.0;
                }
            }
        }

        return 8000.0; // Default men's XC distance
    }

    public static void main(String[] args) {
        RaceScraper scraper = new RaceScraper();
        getCAF cafCalculator = new getCAF();
        writeOutput writer = new writeOutput();

        // 1. Test Single Meet Scraper
        System.out.println("--- Starting 1-meet scraper test ---");
        String testUrl = "https://tfrrs.org/results/xc/26324/Jasper_Fall_XC_Invitational";
        System.out.println("URL: " + testUrl);
        processMeet(testUrl, scraper, cafCalculator, writer);

        // 2. Test Bulk Scraper
        System.out.println("\n--- Starting bulk scraper test ---");
        MeetScraper meetScraper = new MeetScraper();
        // Fetch 1 page of meets to test the bulk functionality
        List<MeetScraper.MeetInfo> meets = meetScraper.scrapeXCMeets(1);
        System.out.println("Found " + meets.size() + " meets from bulk scraper.");

        // Test the pipeline on the first 2 meets from the bulk scraper results
        int limit = Math.min(2, meets.size());
        for (int i = 0; i < limit; i++) {
            MeetScraper.MeetInfo meet = meets.get(i);
            System.out.println("\n--- Processing bulk meet " + (i + 1) + " of " + limit + ": " + meet.name() + " ---");
            System.out.println("URL: " + meet.url());
            processMeet(meet.url(), scraper, cafCalculator, writer);
        }
    }

    private static void processMeet(String meetUrl, RaceScraper scraper, getCAF cafCalculator, writeOutput writer) {
        // 1. Scrape athletes from the meet
        double distance = scraper.detectRaceDistance(meetUrl);
        System.out.println("[Step 1] Detected race distance: " + distance + "m");

        List<Athlete> athletes = scraper.scrapeMensRace(meetUrl);
        System.out.println("[Step 1] Found " + athletes.size() + " athletes.");

        if (athletes.isEmpty()) {
            System.out.println("No athletes found, aborting for this meet.");
            return;
        }

        // 2. Calculate ratings using getCAF
        System.out
                .println("[Step 2] Calculating CAF and prior ratings (this may take a minute due to rate limiting)...");
        // Using 1.06 as default fatigue coefficient for XC
        List<getCAF.AthleteRating> ratings = cafCalculator.getCAF(athletes, distance, 1.06);

        System.out.println(
                "[Step 2] Calculated CAF (Course Adjustment Factor): " + String.format("%.4f", cafCalculator.lastCaf));
        System.out.println("[Step 2] Processed ratings for " + ratings.size() + " athletes.");

        // Print a few sample ratings
        int printLimit = Math.min(ratings.size(), 5);
        System.out.println("Sample Ratings (First " + printLimit + "):");
        for (int i = 0; i < printLimit; i++) {
            getCAF.AthleteRating r = ratings.get(i);
            System.out.println(
                    "  " + r.name() + " | Time: " + r.time() + " | Rating: " + String.format("%.2f", r.rating()));
        }

        // 3. Write output to CSV
        System.out.println("[Step 3] Saving data to CSV...");
        boolean success = writer.write(ratings);

        if (success) {
            System.out.println("--- Meet processing completed successfully. ---");
        } else {
            System.out.println("--- Failed to write CSV. ---");
        }
    }
}
