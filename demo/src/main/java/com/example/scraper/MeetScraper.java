package com.example.scraper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;


public class MeetScraper {

    private static final String BASE_URL = "https://tfrrs.org/results_search_page.html";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    private static final int PAGE_DELAY_MS = 2500; //2.5s between fetches
    private static final int TIMEOUT_MS = 15000;


    public record MeetInfo(String url, String name, String date) {
    }


    public List<MeetInfo> scrapeXCMeets(int maxPages) {
        return scrapeXCMeetsSinceYear(0, maxPages);
    }

    public List<MeetInfo> scrapeXCMeetsSinceYear(int startYear, int maxPagesLimit) {
        List<MeetInfo> meets = new ArrayList<>();

        for (int page = 1; page <= maxPagesLimit; page++) {
            System.out.println("[MeetScraper] Fetching page " + page + " (looking for meets since " + startYear + ")...");

            try {
                String url = BASE_URL + "?with_sports=xc&page=" + page;
                Document doc = Jsoup.connect(url)
                        .userAgent(USER_AGENT)
                        .timeout(TIMEOUT_MS)
                        .get();

                List<MeetInfo> pageMeets = parseMeetPage(doc);

                if (pageMeets.isEmpty()) {
                    System.out.println("No meets found on page " + page );
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

                // Stop only when the vast majority (80%+) of a page's meets predate the start year
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

    private int extractYearFromDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return -1;
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b(20\\d{2})\\b").matcher(dateStr);
            int year = -1;
            while (m.find()) {
                year = Integer.parseInt(m.group(1));
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
