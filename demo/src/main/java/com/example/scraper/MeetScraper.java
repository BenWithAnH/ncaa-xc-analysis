package com.example.scraper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class MeetScraper {

    private static final String BASE_URL = "https://tfrrs.org/results_search_page.html";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    private static final int PAGE_DELAY_MS = 2500; // 2.5s between fetches
    private static final int TIMEOUT_MS = 15000;

    public record MeetInfo(String url, String name, String date) {
    }

    public List<MeetInfo> scrapeXCMeets(int maxPages) {
        return scrapeXCMeetsSinceYear(0, maxPages);
    }

    public List<MeetInfo> scrapeXCMeetPage(int page) {
        int targetPage = Math.max(1, page);
        try {
            String url = BASE_URL + "?with_sports=xc&page=" + targetPage;
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();
            return parseMeetPage(doc);
        } catch (IOException e) {
            System.err.println("Error fetching meet page " + targetPage + ": " + e.getMessage());
            return Collections.emptyList();
        }
    }

    public List<MeetInfo> scrapeXCMeetsSinceYear(int startYear, int maxPagesLimit) {
        List<MeetInfo> meets = new ArrayList<>();

        for (int page = 1; page <= maxPagesLimit; page++) {
            System.out
                    .println("[MeetScraper] Fetching page " + page + " (looking for meets since " + startYear + ")...");

            try {
                String url = BASE_URL + "?with_sports=xc&page=" + page;
                Document doc = Jsoup.connect(url)
                        .userAgent(USER_AGENT)
                        .timeout(TIMEOUT_MS)
                        .get();

                List<MeetInfo> pageMeets = parseMeetPage(doc);

                if (pageMeets.isEmpty()) {
                    System.out.println("No meets found on page " + page);
                    break;
                }

                int oldMeetsCount = 0;
                for (MeetInfo meet : pageMeets) {
                    if (startYear <= 0) {
                        meets.add(meet);
                        continue;
                    }
                    int meetYear = extractYearFromDate(meet.date());
                    if (meetYear > 0 && meetYear < startYear) {
                        oldMeetsCount++;
                        continue;
                    }
                    if (meetYear >= startYear) {
                        meets.add(meet);
                    }
                }

                // Stop only when the vast majority (80%+) of a page's meets predate the start
                // year
                if (startYear > 0 && !pageMeets.isEmpty() && oldMeetsCount >= pageMeets.size() * 0.8) {
                    System.out.println("Reached primarily meets prior to " + startYear
                            + " (" + oldMeetsCount + "/" + pageMeets.size() + " old). Stopping.");
                    break;
                }

                if (page < maxPagesLimit) {
                    Thread.sleep(PAGE_DELAY_MS);
                }

            } catch (IOException e) {
                System.err.println("Error fetching page " + page + ": " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("MeetScraper interrupted");
                break;
            }
        }

        return meets;
    }

    public boolean isPageValid(int page, int startYear) {
        if (page < 1) return false;
        List<MeetInfo> pageMeets = scrapeXCMeetPage(page);
        if (pageMeets.isEmpty()) {
            return false;
        }
        if (startYear <= 0) {
            return true;
        }
        int oldMeetsCount = 0;
        int validDateCount = 0;
        for (MeetInfo meet : pageMeets) {
            int meetYear = extractYearFromDate(meet.date());
            if (meetYear > 0) {
                validDateCount++;
                if (meetYear < startYear) {
                    oldMeetsCount++;
                }
            }
        }
        if (validDateCount == 0) {
            return true;
        }
        return oldMeetsCount < validDateCount * 0.8;
    }

    /**
     * Finds the last valid page returning data using a fast binary search,
     * probing page 100 first to quickly establish baseline pages and meets.
     */
    public int findLastValidPage(int startYear, int maxPagesLimit) {
        int hardMax = (maxPagesLimit > 0) ? maxPagesLimit : 1000;

        if (!isPageValid(1, startYear)) {
            return 0;
        }

        int low = 1;
        int high = Math.min(100, hardMax);

        // Probe page 100 first (or hardMax)
        if (isPageValid(high, startYear)) {
            low = high;
            while (high < hardMax) {
                int nextHigh = Math.min(high * 2, hardMax);
                if (nextHigh == high) break;
                try { Thread.sleep(300); } catch (InterruptedException ignored) {}
                if (isPageValid(nextHigh, startYear)) {
                    low = nextHigh;
                    high = nextHigh;
                } else {
                    high = nextHigh;
                    break;
                }
            }
        } else {
            high = high - 1;
        }

        int lastValid = low;
        while (low <= high) {
            int mid = low + (high - low) / 2;
            if (mid <= 0) break;
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
            if (isPageValid(mid, startYear)) {
                lastValid = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }

        return Math.min(lastValid, hardMax);
    }

    public static int extractYearFromDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty())
            return -1;
        try {
            // Check slash date format with 4-digit year: M/d/yyyy or MM/dd/yyyy
            java.util.regex.Matcher mSlash4 = java.util.regex.Pattern.compile("\\b\\d{1,2}/\\d{1,2}/(20\\d{2})\\b").matcher(dateStr);
            if (mSlash4.find()) {
                return Integer.parseInt(mSlash4.group(1));
            }

            // Check slash date format with 2-digit year: M/d/yy or MM/dd/yy
            java.util.regex.Matcher mSlash2 = java.util.regex.Pattern.compile("\\b\\d{1,2}/\\d{1,2}/(\\d{2})\\b").matcher(dateStr);
            if (mSlash2.find()) {
                return 2000 + Integer.parseInt(mSlash2.group(1));
            }

            // Check standalone 4-digit year e.g. "Nov 2, 2023" or "2024"
            java.util.regex.Matcher m4 = java.util.regex.Pattern.compile("\\b(20\\d{2})\\b").matcher(dateStr);
            int year = -1;
            while (m4.find()) {
                year = Integer.parseInt(m4.group(1));
            }
            return year;
        } catch (Exception e) {
            return -1;
        }
    }

    private List<MeetInfo> parseMeetPage(Document doc) {
        List<MeetInfo> meets = new ArrayList<>();

        Elements rows = doc.select("tbody tr");

        for (Element row : rows) {
            Elements cells = row.select("td");
            if (cells.size() >= 2) {
                String date = cells.get(0).text().trim();
                Element meetLink = cells.get(1).selectFirst("a");

                if (meetLink != null) {
                    String href = meetLink.attr("href").trim();
                    String name = meetLink.text().trim();

                    if (href.contains("/results/xc/")) {
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

}
