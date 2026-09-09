package com.example.scraper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import com.example.dto.IngestionReport;

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

    @Test
    void testExtractAthletesWithAuditAndReconciliation() {
        String html = "<table>" +
                "<tr><th>Place</th><th>Name</th><th>Time</th><th>Year</th></tr>" +
                "<tr><td>1</td><td><a href=\"https://tfrrs.org/athletes/123/A\">John Runner</a></td><td>24:15.2</td><td>SR</td></tr>" +
                "<tr><td>2</td><td>Bob Dnf</td><td>DNF</td><td>JR</td></tr>" +
                "<tr><td>3</td><td>Sam Unattached</td><td>25:30.0</td><td>SO</td></tr>" +
                "<tr class=\"divider\"><th>Place</th><th>Name</th><th>Time</th><th>Year</th></tr>" +
                "</table>";

        Document doc = Jsoup.parse(html);
        RaceScraper scraper = new RaceScraper();
        var table = doc.selectFirst("table");
        assertNotNull(table);

        RaceScraper.ScrapedRaceAudit audit = scraper.extractAthletesWithAudit(table);

        // 3 data rows: John Runner (valid), Bob Dnf (no time), Sam Unattached (no link)
        // 1 sub-header row
        assertEquals(3, audit.totalDataRows());
        assertEquals(1, audit.headerRows());
        assertEquals(1, audit.skippedNoTime(), "Bob DNF should be counted in skippedNoTime");
        assertEquals(1, audit.skippedNoLink(), "Sam Unattached should be counted in skippedNoLink");
        assertEquals(1, audit.athletes().size(), "Only John Runner has valid link and time");
        assertEquals("John Runner", audit.athletes().get(0).name());

        // Reconcile as an IngestionReport
        int savedToDb = audit.athletes().size(); // 1
        int alreadyInDb = 0;
        int discrepancy = audit.totalDataRows() - (savedToDb + alreadyInDb + audit.skippedNoTime() + audit.skippedNoLink());

        IngestionReport report = new IngestionReport(
                "Test Meet",
                "https://tfrrs.org/results/xc/12345",
                "Nov 2, 2023",
                audit.totalDataRows(),
                savedToDb,
                alreadyInDb,
                audit.skippedNoTime(),
                audit.skippedNoLink(),
                discrepancy,
                discrepancy == 0,
                audit.anomalies()
        );

        assertTrue(report.isBalanced());
        assertEquals(0, report.discrepancy());
        String banner = report.toFormattedBanner();
        assertTrue(banner.contains("[ETL-RECONCILE]"));
        assertTrue(banner.contains("[OK] 100% of rows accounted for"));
    }

    @Test
    void testIngestionReportMismatchDetection() {
        // Discrepancy simulated: 10 data rows in HTML, but only 8 accounted for
        int totalDataRows = 10;
        int savedToDb = 6;
        int alreadyInDb = 0;
        int skippedNoTime = 1;
        int skippedNoLink = 1;
        int discrepancy = totalDataRows - (savedToDb + alreadyInDb + skippedNoTime + skippedNoLink); // 2 unaccounted

        IngestionReport report = new IngestionReport(
                "Problematic Meet",
                "https://tfrrs.org/results/xc/99999",
                "Oct 15, 2023",
                totalDataRows,
                savedToDb,
                alreadyInDb,
                skippedNoTime,
                skippedNoLink,
                discrepancy,
                discrepancy == 0,
                List.of("2 row(s) missing or dropped during parsing")
        );

        assertFalse(report.isBalanced());
        assertEquals(2, report.discrepancy());
        String banner = report.toFormattedBanner();
        assertTrue(banner.contains("[MISMATCH DETECTED] 2 row(s) unaccounted for!"));
        assertTrue(banner.contains("Anomalies / Warnings"));
    }
}
