package com.example;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Scrapes the TFRRS results search to discover XC meet links.
 * Paginates through results_search_page.html?with_sports=xc
 */
public class MeetScraper {

    private static final String BASE_URL = "https://tfrrs.org/results_search_page.html";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    private static final int PAGE_DELAY_MS = 2500; // 2.5s between page fetches
    private static final int TIMEOUT_MS = 15000;

    /**
     * Represents a discovered XC meet.
     */
    public record MeetInfo(String url, String name, String date) {
    }

    /**
     * Scrapes up to maxPages of XC meet results from TFRRS.
     * Each page contains ~30 meets.
     *
     * @param maxPages number of result pages to scrape (1-based)
     * @return list of discovered MeetInfo records
     */
    public List<MeetInfo> scrapeXCMeets(int maxPages) {
        List<MeetInfo> meets = new ArrayList<>();

        for (int page = 1; page <= maxPages; page++) {
            System.out.println("[MeetScraper] Fetching page " + page + " of " + maxPages + "...");

            try {
                String url = BASE_URL + "?with_sports=xc&page=" + page;
                Document doc = Jsoup.connect(url)
                        .userAgent(USER_AGENT)
                        .timeout(TIMEOUT_MS)
                        .get();

                List<MeetInfo> pageMeets = parseMeetPage(doc);

                if (pageMeets.isEmpty()) {
                    System.out.println("[MeetScraper] No meets found on page " + page + ", stopping pagination.");
                    break;
                }

                meets.addAll(pageMeets);
                System.out.println("[MeetScraper] Found " + pageMeets.size() + " meets on page " + page
                        + " (total: " + meets.size() + ")");

                // Rate limit between pages
                if (page < maxPages) {
                    Thread.sleep(PAGE_DELAY_MS);
                }

            } catch (IOException e) {
                System.err.println("[MeetScraper] Error fetching page " + page + ": " + e.getMessage());
                // Continue to next page rather than aborting entirely
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("[MeetScraper] Interrupted during pagination.");
                break;
            }
        }

        System.out.println("[MeetScraper] Discovered " + meets.size() + " total XC meets.");
        return meets;
    }

    /**
     * Parses a single search results page and extracts meet info from the table.
     */
    private List<MeetInfo> parseMeetPage(Document doc) {
        List<MeetInfo> meets = new ArrayList<>();

        // The results table has columns: DATE, MEET, SPORT, STATE/PROV
        // Meet links are <a> tags inside the MEET column with href like
        // /results/xc/NNNNN/Meet_Name
        Elements rows = doc.select("tbody tr");

        for (Element row : rows) {
            Elements cells = row.select("td");
            if (cells.size() >= 2) {
                String date = cells.get(0).text().trim();
                Element meetLink = cells.get(1).selectFirst("a");

                if (meetLink != null) {
                    String href = meetLink.attr("href").trim();
                    String name = meetLink.text().trim();

                    // Only include XC results (href contains /results/xc/)
                    if (href.contains("/results/xc/")) {
                        // Build absolute URL if relative
                        String fullUrl;
                        if (href.startsWith("http")) {
                            fullUrl = href;
                        } else {
                            fullUrl = "https://tfrrs.org" + (href.startsWith("/") ? "" : "/") + href;
                        }

                        meets.add(new MeetInfo(fullUrl, name, date));
                    }
                }
            }
        }

        return meets;
    }

    public static void main(String[] args) {
        MeetScraper scraper = new MeetScraper();
        List<MeetInfo> meets = scraper.scrapeXCMeets(1);
        for (MeetInfo meet : meets) {
            System.out.println(meet.date() + " | " + meet.name() + " | " + meet.url());
        }
    }
}
