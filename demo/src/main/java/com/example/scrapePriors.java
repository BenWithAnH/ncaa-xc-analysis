package com.example;

import java.io.IOException;
import java.util.Map;
import java.util.ArrayList;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jsoup.Connection;

public class scrapePriors {

    public String getID(String athleteName) {

        String searchUrl = "https://www.tfrrs.org/search.html";
        String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

        try {
            Connection.Response initialResponse = Jsoup.connect(searchUrl)
                    .timeout(15000)
                    .userAgent(userAgent)
                    .method(Connection.Method.GET)
                    .execute();

            // Save the session cookies so the server remembers us
            Map<String, String> cookies = initialResponse.cookies();

            Document searchPage = initialResponse.parse();
            Element tokenElement = searchPage.selectFirst("input[name=authenticity_token]");
            String authToken = tokenElement != null ? tokenElement.val() : "";

            Document searchResults = Jsoup.connect(searchUrl)
                    .userAgent(userAgent)
                    .cookies(cookies)
                    .data("authenticity_token", authToken)
                    .data("athlete", athleteName)
                    .data("team", "")
                    .data("meet", "")
                    .post();

            // System.out.println(searchResults.text());
            Element targetName = searchResults.selectFirst("#col0 > a:nth-child(1)");
            // System.out.println(targetName.toString());
            String linkOut = targetName.attr("href");
            // System.out.println(linkOut);
            return linkOut;

        } catch (IOException e) {
            e.printStackTrace();
        }

        return "";

    }

    private String getEventPr(Document doc, String query) {
        if (doc == null) {
            return "";
        }
        Element bestsTable = doc.selectFirst("table.bests");
        if (bestsTable == null) {
            return "";
        }
        int currentYear = java.time.Year.now().getValue();
        for (Element td : bestsTable.select("td")) {
            if (td.text().trim().equalsIgnoreCase(query)) {
                Element next = td.nextElementSibling();
                if (next != null) {
                    String time = next.text().trim();
                    Element a = next.selectFirst("a");
                    int yearsOld = 0;
                    if (a != null) {
                        String href = a.attr("href");
                        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(20\\d\\d)").matcher(href);
                        if (m.find()) {
                            int year = Integer.parseInt(m.group(1));
                            yearsOld = currentYear - year;
                            if (yearsOld < 0)
                                yearsOld = 0;
                        }
                    }

                    if (yearsOld > 0) {
                        double seconds = priors.parseTimeToSeconds(time);
                        if (seconds > 0) {
                            // 3% improvement boost per year elapsed
                            double adjustedSeconds = seconds * Math.pow(0.97, yearsOld);
                            return String.format(java.util.Locale.US, "%.2f", adjustedSeconds);
                        }
                    }
                    return time;
                }
            }
        }
        return "";
    }

    public Document getAthleteDocument(String endLink) {
        if (endLink == null || endLink.trim().isEmpty()) {
            return null;
        }
        String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
        try {
            String url;
            if (endLink.startsWith("http://") || endLink.startsWith("https://")) {
                url = endLink;
            } else {
                url = "https://www.tfrrs.org" + (endLink.startsWith("/") ? "" : "/") + endLink;
            }
            return Jsoup.connect(url)
                    .userAgent(userAgent)
                    .get();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public ArrayList<String> getPRs(Document doc) {
        ArrayList<String> prs = new ArrayList<>();
        if (doc == null) {
            return prs;
        }
        prs.add(getEventPr(doc, "800"));
        prs.add(getEventPr(doc, "1500"));
        prs.add(getEventPr(doc, "MILE"));
        prs.add(getEventPr(doc, "3000"));
        prs.add(getEventPr(doc, "5000"));
        prs.add(getEventPr(doc, "10,000"));
        return prs;
    }

    public ArrayList<String> getPRs(String endLink) {
        Document doc = getAthleteDocument(endLink);
        return getPRs(doc);
    }

    public String getGender(Document doc) {
        if (doc == null) {
            return "men"; // fallback
        }
        for (Element a : doc.select("a")) {
            String href = a.attr("href");
            if (href.contains("teams/")) {
                if (href.contains("_college_f_") || href.contains("_w_") || href.contains("/tf/NY_college_f_")
                        || href.toLowerCase().contains("_f_")) {
                    return "women";
                } else if (href.contains("_college_m_") || href.contains("_m_")
                        || href.contains("/tf/NY_college_m_") || href.toLowerCase().contains("_m_")) {
                    return "men";
                }
            }
        }
        return "men"; // fallback
    }

    public String getGender(String endLink) {
        Document doc = getAthleteDocument(endLink);
        return getGender(doc);
    }

    public static void main(String[] args) {
        scrapePriors s = new scrapePriors();
        priors p = new priors();

        // Test Male Athlete
        String nameMale = "Behn Thomas";
        String outMale = s.getID(nameMale);
        if (!outMale.isEmpty()) {
            System.out.println("Athlete: " + nameMale + " (" + outMale + ")");
            String gender = s.getGender(outMale);
            System.out.println("Detected Gender: " + gender);
            ArrayList<String> prs = s.getPRs(outMale);
            p.findBest(prs, gender);
        } else {
            System.out.println("Could not find athlete ID for: " + nameMale);
        }

        // Test Female Athlete
        String nameFemale = "Katelyn Tuohy";
        String outFemale = s.getID(nameFemale);
        if (!outFemale.isEmpty()) {
            System.out.println("\nAthlete: " + nameFemale + " (" + outFemale + ")");
            String gender = s.getGender(outFemale);
            System.out.println("Detected Gender: " + gender);
            ArrayList<String> prs = s.getPRs(outFemale);
            p.findBest(prs, gender);
        } else {
            System.out.println("Could not find athlete ID for: " + nameFemale);
        }
    }
}