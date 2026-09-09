package com.example.scraper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RaceScraperTest {

    @Test
    void testExtractAthletesFromHtmlTable() {
        String html = "<table>" +
                "<tr><th>Place</th><th>Name</th><th>Time</th><th>Year</th></tr>" +
                "<tr><td>1</td><td><a href=\"https://tfrrs.org/athletes/123/A\">John Runner</a></td><td>24:15.2</td><td>SR</td></tr>" +
                "<tr><td>2</td><td><a href=\"https://tfrrs.org/athletes/456/B\">Jane Strider</a></td><td>25:01.0</td><td>JR</td></tr>" +
                "</table>";

        Document doc = Jsoup.parse(html);
        RaceScraper scraper = new RaceScraper();
        
        var table = doc.selectFirst("table");
        assertNotNull(table);
        
        List<String> names = scraper.getColumn(table, "Name");
        assertEquals(List.of("John Runner", "Jane Strider"), names);

        List<String> links = scraper.getAthleteLink(table);
        assertEquals(2, links.size());
        assertTrue(links.get(0).contains("123/A"));

        // Stateless check: calling getAthleteLink without calling getColumn first still works
        RaceScraper freshScraper = new RaceScraper();
        List<String> freshLinks = freshScraper.getAthleteLink(table);
        assertEquals(2, freshLinks.size());
        assertTrue(freshLinks.get(0).contains("123/A"));
    }

    @Test
    void testDetectRaceDistanceFromText() {
        RaceScraper scraper = new RaceScraper();
        Document doc = Jsoup.parse("<h3>Men 8k Run CC</h3>");
        assertEquals(8000.0, scraper.detectRaceDistanceFromDoc(doc));

        Document doc10k = Jsoup.parse("<h3>Men's 10,000 meters</h3>");
        assertEquals(10000.0, scraper.detectRaceDistanceFromDoc(doc10k));
    }
}
