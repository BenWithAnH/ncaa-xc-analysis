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
                    colIndex = i;
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
                        links.add(""); 
                    }
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
            Thread.sleep(MEET_DELAY_MS);

            Document doc = Jsoup.connect(meetUrl)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .maxBodySize(0) 
                    .get();


            Elements headings = doc.select("h3");
            Element mensIndividualTable = null;
            String eventName = "";

            for (Element h3 : headings) {
                String text = h3.text().trim();
                if (text.toLowerCase().contains("men")
                        && !text.toLowerCase().contains("women")
                        && text.toLowerCase().contains("cc")
                        && text.toLowerCase().contains("individual")) {

                    eventName = text;
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
                        break;
                    }
                }
            }

            if (mensIndividualTable == null) {
                mensIndividualTable = findMensTableFallback(doc);
            }

            if (mensIndividualTable == null) {
                System.out.println("[RaceScraper] No men's individual results table found at: " + meetUrl);
                return athletes;
            }

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
        Elements titleDivs = doc.select("div.custom-table-title");
        for (Element titleDiv : titleDivs) {
            String titleText = titleDiv.text().toLowerCase();
            if (titleText.contains("men") && !titleText.contains("women") && titleText.contains("individual")) {
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
                if (table != null) {
                    return table;
                }
            }
        }

        Elements allTables = doc.select("table.tablesaw-xc");
        Element lastAthleteTable = null;
        for (Element table : allTables) {
            Elements athleteLinks = table.select("a[href*=/athletes/]");
            if (!athleteLinks.isEmpty()) {
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
            if (text.contains("men") && !text.contains("women") && text.contains("cc")) {
                // Look for patterns like "8k", "10k", "5k", "6k"
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)k")
                        .matcher(text);
                if (m.find()) {
                    return Double.parseDouble(m.group(1)) * 1000.0;
                }
            }
        }

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

        return 8000.0; 
    }


}
