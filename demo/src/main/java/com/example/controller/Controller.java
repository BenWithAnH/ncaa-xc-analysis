package com.example.controller;

import java.util.List;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.dto.AthleteProfileResponse;
import com.example.dto.AthleteRatingResponse;
import com.example.dto.BulkScrapeStatusResponse;
import com.example.dto.IngestionReport;
import com.example.dto.MeetResponse;
import com.example.dto.RaceRequest;
import com.example.dto.RaceResultResponse;
import com.example.entity.Athlete;
import com.example.entity.RaceResult;
import com.example.service.RaceService;

@RestController
@RequestMapping("/api")
public class Controller {

    private final RaceService raceService;

    public Controller(RaceService raceService) {
        this.raceService = raceService;
    }

    /**
     * Searches athletes by name (up to 4 results for autocomplete).
     *
     * GET /api/athletes/search?query=...
     */
    @GetMapping("/athletes/search")
    public List<Athlete> searchAthletes(@RequestParam String query) {
        return raceService.searchAthletes(query);
    }

    /**
     * Retrieves all database race records associated with an athlete.
     *
     * GET /api/athletes/results?link=...
     */
    @GetMapping("/athletes/results")
    public List<RaceResult> getAthleteResults(@RequestParam String link) {
        return raceService.getAthleteResults(link);
    }

    /**
     * Fetches the top athletes ranked by their rating, including their best time.
     *
     * GET /api/athletes/top
     */
    @GetMapping("/athletes/top")
    public List<Athlete> getTopAthletes() {
        return raceService.getTopAthletes();
    }

    /**
     * Scrapes a meet, calculates the Course Adjustment Factor (CAF),
     * and returns rated athletes.
     *
     * POST /api/race/rate
     * Body: { "meetUrl": "https://tfrrs.org/...", "meetName": "Jasper Fall XC", "meetDate": "Nov 2, 2023" }
     */
    @PostMapping("/race/rate")
    public RaceResultResponse rateRace(@RequestBody RaceRequest request) {
        if (request != null && request.isOverwrite()) {
            return raceService.scrapeAndRateRace(
                    request.getMeetUrl(),
                    request.getMeetName(),
                    request.getMeetDate(),
                    true);
        }
        return raceService.scrapeAndRateRace(
                request.getMeetUrl(),
                request.getMeetName(),
                request.getMeetDate());
    }

    /**
     * Scrapes raw race results (names, times, links) without rating.
     *
     * GET /api/race/results?meetUrl=https://...
     */
    @GetMapping("/race/results")
    public List<AthleteRatingResponse> getRaceResults(@RequestParam String meetUrl) {
        return raceService.scrapeRaceResults(meetUrl);
    }

    /**
     * Lists available XC meets from TFRRS.
     *
     * GET /api/meets?page=1
     * GET /api/meets?maxPages=1
     */
    @GetMapping("/meets")
    public List<MeetResponse> listMeets(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer maxPages) {
        if (page != null) {
            return raceService.listXCMeetsPage(page);
        }
        int pages = (maxPages != null) ? maxPages : 1;
        return raceService.listXCMeets(pages);
    }

    public List<MeetResponse> listMeets(int maxPages) {
        return listMeets(null, maxPages);
    }
    
    /**
     * Bulk scrapes multiple meets from TFRRS and saves results to PostgreSQL.
     *
     * POST /api/meets/bulk-scrape?maxPages=1
     */
    @PostMapping("/meets/bulk-scrape")
    public String bulkScrape(@RequestParam(defaultValue = "1") int maxPages) {
        int totalSaved = raceService.bulkScrapeMeets(maxPages);
        return "Bulk scrape completed. Saved " + totalSaved + " athletes across " + maxPages + " page(s) of meets.";
    }
    
    /**
     * Bulk scrapes meets from TFRRS going back to a specific year and saves results asynchronously.
     *
     * POST /api/meets/bulk-scrape-since-year?startYear=2023&maxPages=50
     */
    @PostMapping("/meets/bulk-scrape-since-year")
    public String bulkScrapeSinceYear(@RequestParam int startYear, @RequestParam(defaultValue = "50") int maxPages) {
        boolean started = raceService.startBulkScrapeSinceYearAsync(startYear, maxPages);
        if (!started) {
            return "A bulk scrape is already in progress.";
        }
        return "Bulk scrape started for meets since year " + startYear + ".";
    }

    /**
     * Retrieves the current bulk scrape progress, average meet processing time, and ETA.
     *
     * GET /api/meets/bulk-scrape/status
     */
    @GetMapping("/meets/bulk-scrape/status")
    public BulkScrapeStatusResponse getBulkScrapeStatus() {
        return raceService.getBulkScrapeStatus();
    }

    /**
     * Cancels an ongoing bulk scrape job.
     *
     * POST /api/meets/bulk-scrape/cancel
     */
    @PostMapping("/meets/bulk-scrape/cancel")
    public String cancelBulkScrape() {
        raceService.cancelBulkScrape();
        return "Cancellation requested.";
    }

    /**
     * Fetches an athlete's PRs and prior rating from their TFRRS profile.
     *
     * GET /api/athlete/profile?link=https://...
     */
    @GetMapping("/athlete/profile")
    public AthleteProfileResponse getAthleteProfile(@RequestParam String link) {
        return raceService.getAthleteProfile(link);
    }

    /**
     * Retrieves recent ETL reconciliation reports from meet scraping.
     *
     * GET /api/meets/reconciliation-reports
     */
    @GetMapping("/meets/reconciliation-reports")
    public List<IngestionReport> getReconciliationReports() {
        return raceService.getRecentIngestionReports();
    }
}