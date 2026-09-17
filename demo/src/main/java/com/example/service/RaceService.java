package com.example.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.scraper.MeetScraper;
import com.example.scraper.RaceScraper;
import com.example.scraper.RaceScraper.Athlete;
import com.example.dto.AthleteProfileResponse;
import com.example.dto.AthleteRatingResponse;
import com.example.dto.IngestionReport;
import com.example.dto.MeetResponse;
import com.example.dto.RaceResultResponse;
import com.example.GetCAF;
import com.example.Priors;
import com.example.scraper.ScrapePriors;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

import com.example.entity.RaceResult;
import com.example.repository.AthleteRepository;
import com.example.repository.RaceResultRepository;

/**
 * Service layer that wraps the existing scraping and rating logic,
 * exposing clean methods for the REST controller.
 */
@Service
public class RaceService {

    private final AthleteRepository athleteRepository;
    private final RaceResultRepository raceResultRepository;
    private final List<IngestionReport> recentReports = java.util.Collections.synchronizedList(new ArrayList<>());

    public RaceService(AthleteRepository athleteRepository, RaceResultRepository raceResultRepository) {
        this.athleteRepository = athleteRepository;
        this.raceResultRepository = raceResultRepository;
    }

    void recordReport(IngestionReport report) {
        if (recentReports.size() >= 100) {
            recentReports.remove(0);
        }
        recentReports.add(report);
        logReport(report);
        appendReportToFile(report);
    }

    private void logReport(IngestionReport report) {
        System.out.println(report.toCompactLine());
    }

    private synchronized void appendReportToFile(IngestionReport report) {
        try {
            Path logPath = Paths.get("reconciliation_reports.log");
            Files.writeString(logPath,
                    report.toCompactLine() + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            Path csvPath = Paths.get("reconciliation_reports.csv");
            if (!Files.exists(csvPath) || Files.size(csvPath) == 0) {
                Files.writeString(csvPath, IngestionReport.csvHeader() + System.lineSeparator(),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
            Files.writeString(csvPath, report.toCsvRow() + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("[MEET-LOG] Failed to write report file: " + e.getMessage());
        }
    }

    public List<IngestionReport> getRecentIngestionReports() {
        return new ArrayList<>(recentReports);
    }

    /**
     * Scrapes a meet page, calculates CAF, and returns fully-rated athletes.
     * It also saves the RaceResults to the database.
     *
     * @param meetUrl             TFRRS meet URL
     * @return RaceResultResponse with CAF, distance, and athlete ratings
     */
    @Transactional
    public RaceResultResponse scrapeAndRateRace(String meetUrl, String meetName, String meetDate) {
        if (meetUrl == null || meetUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("meetUrl is required");
        }

        RaceScraper raceScraper = new RaceScraper();
        GetCAF cafCalculator = new GetCAF(athleteRepository);

        // Fetch the meet page ONCE and reuse the document for both distance detection and scraping
        Document doc;
        try {
            Thread.sleep(2000); // rate limit
            doc = Jsoup.connect(meetUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(15000)
                    .maxBodySize(0)
                    .get();
        } catch (IOException e) {
            String err = "Error fetching meet page: " + meetUrl + " - " + e.getMessage();
            IngestionReport failedReport = new IngestionReport(
                meetName != null && !meetName.isEmpty() ? meetName : extractMeetName(meetUrl),
                meetUrl, meetDate != null && !meetDate.isEmpty() ? meetDate : "TBD",
                0, 0, 0, 0, 0, 0, false, List.of(err)
            );
            recordReport(failedReport);
            return new RaceResultResponse(meetUrl, 8000.0, 1.0, new ArrayList<>());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            String err = "Interrupted while scraping meet: " + meetUrl;
            IngestionReport failedReport = new IngestionReport(
                meetName != null && !meetName.isEmpty() ? meetName : extractMeetName(meetUrl),
                meetUrl, meetDate != null && !meetDate.isEmpty() ? meetDate : "TBD",
                0, 0, 0, 0, 0, 0, false, List.of(err)
            );
            recordReport(failedReport);
            return new RaceResultResponse(meetUrl, 8000.0, 1.0, new ArrayList<>());
        }

        double distance = raceScraper.detectRaceDistanceFromDoc(doc);

        RaceScraper.ScrapedRaceAudit audit = raceScraper.scrapeMensRaceWithAudit(doc, meetUrl);
        List<Athlete> athletes = audit.athletes();
        String actualMeetName = (meetName == null || meetName.trim().isEmpty()) ? extractMeetName(meetUrl) : meetName;
        String actualDate = (meetDate == null || meetDate.trim().isEmpty()) ? "TBD" : meetDate;

        if (athletes.isEmpty() && audit.totalDataRows() == 0) {
            IngestionReport emptyReport = new IngestionReport(
                actualMeetName, meetUrl, actualDate, 0, 0, 0, 0, 0, 0, true,
                List.of("No men's individual results table found at: " + meetUrl)
            );
            recordReport(emptyReport);
            return new RaceResultResponse(meetUrl, distance, 1.0, new ArrayList<>());
        }

        List<GetCAF.AthleteRating> ratings = cafCalculator.getCAF(athletes, distance);

        List<AthleteRatingResponse> dtoList = new ArrayList<>();

        java.util.Set<String> existingResultLinks = raceResultRepository.findByMeetName(actualMeetName)
                .stream().map(RaceResult::getAthleteLink).collect(Collectors.toSet());

        List<String> validLinks = ratings.stream()
                .map(GetCAF.AthleteRating::link)
                .filter(l -> l != null && !l.isEmpty())
                .collect(Collectors.toList());

        java.util.Map<String, com.example.entity.Athlete> existingAthletes = new java.util.HashMap<>();
        if (!validLinks.isEmpty()) {
            athleteRepository.findAllById(validLinks).forEach(a -> existingAthletes.put(a.getLink(), a));
        }

        List<RaceResult> resultsToSave = new ArrayList<>();
        List<com.example.entity.Athlete> athletesToSave = new ArrayList<>();
        int alreadyInDb = 0;

        for (GetCAF.AthleteRating r : ratings) {
            dtoList.add(new AthleteRatingResponse(r.name(), r.time(), r.link(), r.rating()));
            
            if (r.link() != null && !r.link().isEmpty()) {
                if (!existingResultLinks.contains(r.link())) {
                    RaceResult result = new RaceResult(
                            r.link(),
                            r.name(),
                            actualMeetName,
                            actualDate,
                            r.time(),
                            r.rating() 
                    );
                    resultsToSave.add(result);
                    existingResultLinks.add(r.link());
                } else {
                    alreadyInDb++;
                }

                com.example.entity.Athlete athlete = existingAthletes.get(r.link());
                if (athlete == null) {
                    athlete = new com.example.entity.Athlete(r.link(), r.name(), r.rating());
                    athlete.setBestTime(r.time());
                    athletesToSave.add(athlete);
                    existingAthletes.put(r.link(), athlete);
                } else {
                    String currentBest = athlete.getBestTime();
                    boolean updated = false;
                    
                    if (currentBest == null || currentBest.isEmpty()) {
                        athlete.setBestTime(r.time());
                        athlete.setRating(r.rating());
                        updated = true;
                    } else {
                        double currentBestSeconds = Priors.parseTimeToSeconds(currentBest);
                        double newSeconds = Priors.parseTimeToSeconds(r.time());
                        
                        if (newSeconds > 0 && newSeconds < currentBestSeconds) {
                            athlete.setBestTime(r.time());
                            updated = true;
                        }
                        
                        double currentRating = athlete.getRating() != null ? athlete.getRating() : 0.0;
                        if (r.rating() > currentRating) {
                            athlete.setRating(r.rating());
                            updated = true;
                        }
                    }
                    if (updated) {
                        athletesToSave.add(athlete);
                    }
                }
            }
        }

        if (!resultsToSave.isEmpty()) {
            raceResultRepository.saveAll(resultsToSave);
        }
        if (!athletesToSave.isEmpty()) {
            athleteRepository.saveAll(athletesToSave);
        }

        int savedCount = resultsToSave.size();
        int droppedInRating = Math.max(0, athletes.size() - ratings.size());
        int accounted = savedCount + alreadyInDb + audit.skippedNoTime() + audit.skippedNoLink() + droppedInRating;
        int discrepancy = audit.totalDataRows() - accounted;
        boolean isBalanced = (discrepancy == 0);

        List<String> anomalies = new ArrayList<>(audit.anomalies());
        if (droppedInRating > 0) {
            anomalies.add(droppedInRating + " athlete(s) dropped during rating/CAF calculation (invalid time or unparseable)");
        }
        if (!isBalanced) {
            anomalies.add(String.format("Accounting mismatch: totalDataRows=%d, saved=%d, alreadyInDb=%d, skippedNoTime=%d, skippedNoLink=%d, droppedInRating=%d (discrepancy=%d)",
                audit.totalDataRows(), savedCount, alreadyInDb, audit.skippedNoTime(), audit.skippedNoLink(), droppedInRating, discrepancy));
        }

        IngestionReport report = new IngestionReport(
            actualMeetName, meetUrl, actualDate, audit.totalDataRows(), savedCount, alreadyInDb,
            audit.skippedNoTime(), audit.skippedNoLink(), discrepancy, isBalanced, anomalies
        );
        recordReport(report);

        return new RaceResultResponse(meetUrl, distance, cafCalculator.lastCaf, dtoList);
    }
    
    private String extractMeetName(String url) {
        if (url == null) return "Unknown Meet";
        String[] parts = url.split("/");
        return parts[parts.length - 1].replace("_", " ");
    }


    public int bulkScrapeMeets(int maxPages) {
        return bulkScrapeMeetsSinceYear(0, maxPages);
    }

    /**
     * Scrapes all meets going back to the specified year and saves them.
     * Uses the fast raw scraping path (no CAF/rating computation).
     *
     * @param startYear the earliest year to include (e.g. 2023)
     * @param maxPagesLimit a safety limit on max pages to fetch
     * @return the total number of athlete results saved
     */
    public int bulkScrapeMeetsSinceYear(int startYear, int maxPagesLimit) {
        if (maxPagesLimit < 1) {
            throw new IllegalArgumentException("maxPagesLimit must be at least 1");
        }

        MeetScraper meetScraper = new MeetScraper();
        List<MeetScraper.MeetInfo> meets = meetScraper.scrapeXCMeetsSinceYear(startYear, maxPagesLimit);
        int totalSaved = 0;

        System.out.println("[MEET-LOG] Bulk scrape started for " + meets.size() + " meet(s). Logging every meet to terminal and reconciliation_reports.csv");

        for (int i = 0; i < meets.size(); i++) {
            MeetScraper.MeetInfo meet = meets.get(i);
            try {
                totalSaved += bulkScrapeAndSaveRaw(meet.url(), meet.name(), meet.date());
            } catch (Exception e) {
                String err = "Error processing meet " + meet.name() + ": " + e.getMessage();
                IngestionReport failedReport = new IngestionReport(
                        meet.name(), meet.url(), meet.date(), 0, 0, 0, 0, 0, 0, false, List.of(err)
                );
                recordReport(failedReport);
            }
        }
        
        return totalSaved;
    }

    /**
     * Fast bulk scraping: fetches a meet page, extracts men's race results,
     * and saves them to the database WITHOUT computing CAF/ratings.
     * This avoids the N+1 athlete profile scraping that makes the rated path slow.
     * Ratings can be computed later on-demand.
     *
     * @param meetUrl  TFRRS meet URL
     * @param meetName meet name for storage
     * @param meetDate meet date string
     * @return number of results saved
     */
    public int bulkScrapeAndSaveRaw(String meetUrl, String meetName, String meetDate) {
        return bulkScrapeAndSaveRawWithReport(meetUrl, meetName, meetDate).savedToDb();
    }

    /**
     * Fast bulk scraping with ETL reconciliation: fetches a meet page, extracts men's race results,
     * reconciles extracted rows against DB existing and skipped rows, saves new results,
     * and logs a structured [ETL-RECONCILE] report.
     *
     * @param meetUrl  TFRRS meet URL
     * @param meetName meet name for storage
     * @param meetDate meet date string
     * @return IngestionReport containing complete reconciliation metrics
     */
    public IngestionReport bulkScrapeAndSaveRawWithReport(String meetUrl, String meetName, String meetDate) {
        RaceScraper raceScraper = new RaceScraper();

        Document doc;
        try {
            Thread.sleep(2000); // rate limit
            doc = Jsoup.connect(meetUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(15000)
                    .maxBodySize(0)
                    .get();
        } catch (IOException e) {
            String err = "Error fetching meet page: " + meetUrl + " - " + e.getMessage();
            IngestionReport failedReport = new IngestionReport(
                meetName != null && !meetName.isEmpty() ? meetName : extractMeetName(meetUrl),
                meetUrl, meetDate != null && !meetDate.isEmpty() ? meetDate : "TBD",
                0, 0, 0, 0, 0, 0, false, List.of(err)
            );
            recordReport(failedReport);
            return failedReport;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            String err = "Interrupted while fetching " + meetUrl;
            IngestionReport failedReport = new IngestionReport(
                meetName != null && !meetName.isEmpty() ? meetName : extractMeetName(meetUrl),
                meetUrl, meetDate != null && !meetDate.isEmpty() ? meetDate : "TBD",
                0, 0, 0, 0, 0, 0, false, List.of(err)
            );
            recordReport(failedReport);
            return failedReport;
        }

        double distance = raceScraper.detectRaceDistanceFromDoc(doc);
        RaceScraper.ScrapedRaceAudit audit = raceScraper.scrapeMensRaceWithAudit(doc, meetUrl);
        List<Athlete> athletes = audit.athletes();

        String actualMeetName = (meetName == null || meetName.trim().isEmpty()) ? extractMeetName(meetUrl) : meetName;
        String actualDate = (meetDate == null || meetDate.trim().isEmpty()) ? "TBD" : meetDate;

        if (athletes.isEmpty() && audit.totalDataRows() == 0) {
            IngestionReport emptyReport = new IngestionReport(
                actualMeetName, meetUrl, actualDate, 0, 0, 0, 0, 0, 0, true,
                List.of("No men's individual results table found at: " + meetUrl)
            );
            recordReport(emptyReport);
            return emptyReport;
        }

        // Check which results already exist for this meet to avoid duplicates
        java.util.Set<String> existingResultLinks = raceResultRepository.findByMeetName(actualMeetName)
                .stream().map(RaceResult::getAthleteLink).collect(Collectors.toSet());

        List<RaceResult> resultsToSave = new ArrayList<>();
        int alreadyInDb = 0;
        List<String> anomalies = new ArrayList<>(audit.anomalies());

        for (Athlete a : athletes) {
            if (a.link() == null || a.link().isEmpty()) {
                anomalies.add("Athlete missing link encountered during save: " + a.name());
                continue;
            }
            if (existingResultLinks.contains(a.link())) {
                alreadyInDb++;
            } else {
                RaceResult result = new RaceResult(
                        a.link(),
                        a.name(),
                        actualMeetName,
                        actualDate,
                        a.time(),
                        null  // No rating computed during bulk scrape
                );
                resultsToSave.add(result);
                existingResultLinks.add(a.link());
            }
        }

        if (!resultsToSave.isEmpty()) {
            raceResultRepository.saveAll(resultsToSave);
        }

        int savedCount = resultsToSave.size();
        int accounted = savedCount + alreadyInDb + audit.skippedNoTime() + audit.skippedNoLink();
        int discrepancy = audit.totalDataRows() - accounted;
        boolean isBalanced = (discrepancy == 0);

        if (!isBalanced) {
            anomalies.add(String.format(
                "Accounting mismatch: totalDataRows=%d, saved=%d, alreadyInDb=%d, skippedNoTime=%d, skippedNoLink=%d (discrepancy=%d)",
                audit.totalDataRows(), savedCount, alreadyInDb, audit.skippedNoTime(), audit.skippedNoLink(), discrepancy
            ));
        }

        IngestionReport report = new IngestionReport(
            actualMeetName,
            meetUrl,
            actualDate,
            audit.totalDataRows(),
            savedCount,
            alreadyInDb,
            audit.skippedNoTime(),
            audit.skippedNoLink(),
            discrepancy,
            isBalanced,
            anomalies
        );

        recordReport(report);

        return report;
    }

    /**
     * Scrapes raw race results (no CAF/rating calculation).
     *
     * @param meetUrl TFRRS meet URL
     * @return list of athlete name/time/link without ratings
     */
    public List<AthleteRatingResponse> scrapeRaceResults(String meetUrl) {
        if (meetUrl == null || meetUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("meetUrl is required");
        }

        RaceScraper raceScraper = new RaceScraper();
        List<Athlete> athletes = raceScraper.scrapeMensRace(meetUrl);

        return athletes.stream()
                .map(a -> new AthleteRatingResponse(a.name(), a.time(), a.link(), 0.0))
                .collect(Collectors.toList());
    }

    /**
     * Lists available XC meets from TFRRS.
     *
     * @param maxPages number of pages to scrape (each page ≈ 30 meets)
     * @return list of meet info
     */
    public List<MeetResponse> listXCMeets(int maxPages) {
        if (maxPages < 1) {
            throw new IllegalArgumentException("maxPages must be at least 1");
        }

        MeetScraper meetScraper = new MeetScraper();
        List<MeetScraper.MeetInfo> meets = meetScraper.scrapeXCMeets(maxPages);

        return meets.stream()
                .map(m -> new MeetResponse(m.url(), m.name(), m.date()))
                .collect(Collectors.toList());
    }

    /**
     * Fetches an athlete's profile page and returns their PRs and prior rating.
     *
     * @param athleteLink TFRRS athlete profile URL or path
     * @return profile with gender, PRs, and computed prior rating
     */
    public AthleteProfileResponse getAthleteProfile(String athleteLink) {
        if (athleteLink == null || athleteLink.trim().isEmpty()) {
            throw new IllegalArgumentException("athlete link is required");
        }
        
        // Cache Check
        var cachedAthlete = athleteRepository.findById(athleteLink);
        if (cachedAthlete.isPresent() && cachedAthlete.get().getRating() != null) {
            return new AthleteProfileResponse("Cached", new ArrayList<>(), cachedAthlete.get().getRating());
        }

        ScrapePriors scraper = new ScrapePriors();
        Priors priorsCalculator = new Priors();

        org.jsoup.nodes.Document doc = scraper.getAthleteDocument(athleteLink);
        if (doc == null) {
            throw new IllegalArgumentException("Could not fetch athlete page for: " + athleteLink);
        }

        String gender = scraper.getGender(doc);
        ArrayList<String> prs = scraper.getPRs(doc);
        double priorRating = priorsCalculator.getPriorRating(prs, gender);

        return new AthleteProfileResponse(gender, prs, priorRating);
    }

    /**
     * Retrieves the top 100 athletes by their rating (highest first).
     */
    public List<com.example.entity.Athlete> getTopAthletes() {
        return athleteRepository.findTop100ByBestTimeIsNotNullOrderByRatingDesc();
    }

    /**
     * Searches for up to 4 athletes matching the name query.
     */
    public List<com.example.entity.Athlete> searchAthletes(String query) {
        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }
        return athleteRepository.findTop4ByNameContainingIgnoreCase(query.trim());
    }

    /**
     * Retrieves all stored race results for a specific athlete.
     */
    public List<RaceResult> getAthleteResults(String athleteLink) {
        if (athleteLink == null || athleteLink.trim().isEmpty()) {
            return List.of();
        }
        return raceResultRepository.findByAthleteLink(athleteLink.trim());
    }
}
