package com.example;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class parseRace {
    private int nameIndex = -1;

    public record Athlete(String name, String time, String link) {
    }

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

    public List<Athlete> getCAF(String raceLink) {

        try {
            String url = raceLink;
            Document doc = Jsoup.connect(url)
                    .get();

            // System.out.println(doc.text());
            Element el = doc.selectFirst("table:contains(Year)");
            // System.out.println(el.text());
            List<String> names = getColumn(el, "Name");
            List<String> times = getColumn(el, "Time");
            List<String> links = getAthleteLink(el);
            List<Athlete> athletes = new ArrayList<>();
            int limit = Math.min(names.size(), Math.min(times.size(), links.size()));
            for (int i = 0; i < limit; i++) {
                athletes.add(new Athlete(names.get(i), times.get(i), links.get(i)));
            }

            // System.out.println(athletes);
            return athletes;

        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static void main(String[] args) {
        parseRace p = new parseRace();
        p.getCAF("https://tfrrs.org/results/xc/26324/Jasper_Fall_XC_Invitational");
    }
}
