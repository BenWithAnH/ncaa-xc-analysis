package com.example.scraper;

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
 * getAthleteLink)
 */
public class RaceScraper {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    private static final int TIMEOUT_MS = 15000;
    private static final int MEET_DELAY_MS = 2000; // 2s delay before fetching


    public record Athlete(String name, String time, String link) {
    }

    private int findColumnIndex(Element el, String colName) {
        if (el == null) return -1;
        Element headerRow = el.selectFirst("tr");
        if (headerRow != null) {
            Elements headers = headerRow.select("th, td");
            for (int i = 0; i < headers.size(); i++) {
                if (headers.get(i).text().trim().equalsIgnoreCase(colName)) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Extracts all values from a named column in an HTML table.
     * The first row is assumed to be the header row.
     */
    public List<String> getColumn(Element el, String rowName) {
        List<String> columnValues = new ArrayList<>();
        int colIndex = findColumnIndex(el, rowName);
        if (colIndex != -1) {
            Elements rows = el.select("tr");
            for (int i = 1; i < rows.size(); i++) {
                Elements cells = rows.get(i).select("th, td");
                if (cells.size() > colIndex) {
                    columnValues.add(cells.get(colIndex).text());
                }
            }
        }
        return columnValues;
    }

    /**
     * Extracts athlete profile links from the Name column of an HTML table.
     */
    public List<String> getAthleteLink(Element el) {
        List<String> links = new ArrayList<>();
        int colIndex = findColumnIndex(el, "Name");
        if (colIndex != -1) {
            Elements rows = el.select("tr");
            for (int i = 1; i < rows.size(); i++) {
                Elements cells = rows.get(i).select("th, td");
                if (cells.size() > colIndex) {
                    Element link = cells.get(colIndex).selectFirst("a");
                    links.add(link != null ? link.absUrl("href").replaceAll("\\s+", "") : "");
                }
            }
        }
        return links;
    }


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
            return extractAthletesFromTable(el);

        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }


    /**
     * Scrapes the men's individual race results from a TFRRS XC meet page.
     *
     * @param meetUrl full URL to the meet results page
     * @return list of Athlete records with name, time, and profile link; empty list
     *         on failure
     */
    public List<Athlete> scrapeMensRace(String meetUrl) {
        try {
            Thread.sleep(MEET_DELAY_MS);

            Document doc = Jsoup.connect(meetUrl)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .maxBodySize(0) 
                    .get();

            return scrapeMensRaceFromDoc(doc, meetUrl);

        } catch (IOException e) {
            System.err.println("[RaceScraper] Error fetching meet page: " + meetUrl + " - " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[RaceScraper] Interrupted while scraping: " + meetUrl);
        }

        return new ArrayList<>();
    }

    /**
     * Scrapes the men's individual race results from a pre-fetched TFRRS meet document.
     * Use this to avoid duplicate HTTP requests when you already have the document.
     *
     * @param doc  the pre-fetched Jsoup Document
     * @param meetUrl the original URL (for logging only)
     * @return list of Athlete records with name, time, and profile link; empty list
     *         on failure
     */
    public List<Athlete> scrapeMensRaceFromDoc(Document doc, String meetUrl) {
        List<Athlete> athletes = new ArrayList<>();

        Elements headings = doc.select("h3");
        List<Element> mensTables = new ArrayList<>();
        String eventName = "";

        for (Element h3 : headings) {
            String text = h3.text().trim().toLowerCase();
            if (text.contains("men") && !text.contains("women")) {
                eventName = text;
                Element mensIndividualTable = null;
                Element container = h3.parent();
                if (container != null) {
                    Element tableParent = container.parent();
                    if (tableParent != null) {
                        mensIndividualTable = tableParent.selectFirst("table");
                    }
                }

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
                    if (tableHasTimeColumn(mensIndividualTable)) {
                        mensTables.add(mensIndividualTable);
                    }
                }
            }
        }

        if (mensTables.isEmpty()) {
            mensTables = findMensTablesFallback(doc);
        }

        if (mensTables.isEmpty()) {
            System.out.println("[RaceScraper] No men's individual results table found at: " + meetUrl);
            return athletes;
        }

        for (Element table : mensTables) {
            athletes.addAll(extractAthletesFromTable(table));
        }

        if (!eventName.isEmpty()) {
            System.out.println("[RaceScraper] Found " + athletes.size() + " athletes in: " + eventName + " (and potentially other men's races)");
        }

        return athletes;
    }

    /**
     * Checks whether a table has a "TIME" column header (case-insensitive).
     * This is used to distinguish individual results tables (which have a Time column)
     * from team results tables (which don't).
     */
    private boolean tableHasTimeColumn(Element table) {
        Element headerRow = table.selectFirst("tr");
        if (headerRow != null) {
            for (Element header : headerRow.select("th, td")) {
                if (header.text().trim().equalsIgnoreCase("time")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Fallback method: find the men's individual results table by looking for
     * tables that contain athlete links and are associated with a "Men" event.
     */
    private List<Element> findMensTablesFallback(Document doc) {
        List<Element> tables = new ArrayList<>();
        Elements titleDivs = doc.select("div.custom-table-title");
        for (Element titleDiv : titleDivs) {
            String titleText = titleDiv.text().toLowerCase();
            if (titleText.contains("men") && !titleText.contains("women")) {
                Element table = null;
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
                if (table != null && tableHasTimeColumn(table)) {
                    tables.add(table);
                }
            }
        }

        if (tables.isEmpty()) {
            Elements allTables = doc.select("table.tablesaw-xc");
            for (Element table : allTables) {
                Elements athleteLinks = table.select("a[href*=/athletes/]");
                if (!athleteLinks.isEmpty()) {
                    Elements teamLinks = table.select("a[href*=_college_m_], a[href*=_m_]");
                    if (!teamLinks.isEmpty() && tableHasTimeColumn(table)) {
                        tables.add(table);
                    }
                }
            }
        }

        return tables;
    }

    /**
     * Extracts athletes from a results table using getColumn() and
     * getAthleteLink().
     */
    private List<Athlete> extractAthletesFromTable(Element table) {
        if (table == null) return new ArrayList<>();
        int nameCol = findColumnIndex(table, "Name");
        int timeCol = findColumnIndex(table, "Time");
        if (nameCol == -1 || timeCol == -1) return new ArrayList<>();

        List<Athlete> athletes = new ArrayList<>();
        Elements rows = table.select("tr");
        for (int i = 1; i < rows.size(); i++) {
            Elements cells = rows.get(i).select("th, td");
            if (cells.size() > nameCol && cells.size() > timeCol) {
                String name = cells.get(nameCol).text().trim();
                String time = cells.get(timeCol).text().trim();

                if (name.isEmpty() || time.isEmpty()) {
                    continue;
                }

                Element linkEl = cells.get(nameCol).selectFirst("a");
                String link = linkEl != null ? linkEl.absUrl("href").replaceAll("\\s+", "") : "";

                athletes.add(new Athlete(name, time, link));
            }
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
        ScrapePriors scraper = new ScrapePriors();
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
            if (text.contains("men") && !text.contains("women")) {
                double distance = parseDistanceFromText(text);
                if (distance > 0) return distance;
            }
        }

        Elements eventLinks = doc.select("ol.events-list a, select#quick-links-select option");
        for (Element el : eventLinks) {
            String text = el.text().trim().toLowerCase();
            if (text.contains("men") && !text.contains("women")) {
                double distance = parseDistanceFromText(text);
                if (distance > 0) return distance;
            }
        }

        return 8000.0; 
    }

    /**
     * Parses distance from event text. Handles formats like:
     * - "men 8k run cc" → 8000.0
     * - "men's 8000 meters" → 8000.0
     * - "men 10k run cc" → 10000.0
     */
    private double parseDistanceFromText(String text) {
        if (text == null) return 0.0;
        String normalized = text.replace(",", "");
        // Try "Nk" pattern first (e.g., "8k", "10k")
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)k")
                .matcher(normalized);
        if (m.find()) {
            return Double.parseDouble(m.group(1)) * 1000.0;
        }
        // Try "N meters" or "N meter" pattern (e.g., "8000 meters", "10,000 meters")
        m = java.util.regex.Pattern.compile("(\\d+)\\s*meters?")
                .matcher(normalized);
        if (m.find()) {
            return Double.parseDouble(m.group(1));
        }
        return 0.0;
    }


}
