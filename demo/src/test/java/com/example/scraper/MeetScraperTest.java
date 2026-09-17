package com.example.scraper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class MeetScraperTest {

    @Test
    void testExtractYearFromDate() {
        assertEquals(2026, MeetScraper.extractYearFromDate("09/17/26"));
        assertEquals(2022, MeetScraper.extractYearFromDate("10/08/22"));
        assertEquals(2023, MeetScraper.extractYearFromDate("Nov 2, 2023"));
        assertEquals(2024, MeetScraper.extractYearFromDate("2024"));
        assertEquals(-1, MeetScraper.extractYearFromDate(null));
        assertEquals(-1, MeetScraper.extractYearFromDate(""));
        assertEquals(-1, MeetScraper.extractYearFromDate("invalid date"));
    }

    @Test
    void testBinarySearchLogic() {
        // Subclass to simulate pages: valid up to page 14
        MeetScraper testScraper = new MeetScraper() {
            @Override
            public boolean isPageValid(int page, int startYear) {
                return page >= 1 && page <= 14;
            }
        };

        int lastPage = testScraper.findLastValidPage(2026, 200);
        assertEquals(14, lastPage);
    }

    @Test
    void testBinarySearchWhenPage1Invalid() {
        MeetScraper testScraper = new MeetScraper() {
            @Override
            public boolean isPageValid(int page, int startYear) {
                return false;
            }
        };

        int lastPage = testScraper.findLastValidPage(2099, 100);
        assertEquals(0, lastPage);
    }

    @Test
    void testBinarySearchWhenAllPagesValidUpToMax() {
        MeetScraper testScraper = new MeetScraper() {
            @Override
            public boolean isPageValid(int page, int startYear) {
                return true;
            }
        };

        int lastPage = testScraper.findLastValidPage(2020, 50);
        assertEquals(50, lastPage);
    }
}
