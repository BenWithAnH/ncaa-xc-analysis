package com.example;

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
    private static final int PAGE_DELAY_MS = 2500; //2.5s between page fetches
    private static final int TIMEOUT_MS = 15000;


    public record MeetInfo(String url, String name, String date) {
    }


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
                    System.out.println("No meets found on page " + page );
                    break;
                }

                meets.addAll(pageMeets);

                if (page < maxPages) {
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
