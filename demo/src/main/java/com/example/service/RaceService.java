package com.example.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.scraper.MeetScraper;
import com.example.scraper.RaceScraper;
import com.example.scraper.RaceScraper.Athlete;
import com.example.dto.AthleteProfileResponse;
import com.example.dto.AthleteRatingResponse;
import com.example.dto.MeetResponse;
import com.example.dto.RaceResultResponse;
import com.example.GetCAF;
import com.example.Priors;
import com.example.scraper.ScrapePriors;

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

    public RaceService(AthleteRepository athleteRepository, RaceResultRepository raceResultRepository) {
        this.athleteRepository = athleteRepository;
        this.raceResultRepository = raceResultRepository;
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
            System.err.println("[RaceService] Error fetching meet page: " + meetUrl + " - " + e.getMessage());
            return new RaceResultResponse(meetUrl, 8000.0, 1.0, new ArrayList<>());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new RaceResultResponse(meetUrl, 8000.0, 1.0, new ArrayList<>());
        }

        double distance = raceScraper.detectRaceDistanceFromDoc(doc);

        List<Athlete> athletes = raceScraper.scrapeMensRaceFromDoc(doc, meetUrl);
        if (athletes.isEmpty()) {
            return new RaceResultResponse(meetUrl, distance, 1.0, new ArrayList<>());
        }

        List<GetCAF.AthleteRating> ratings = cafCalculator.getCAF(athletes, distance);

        List<AthleteRatingResponse> dtoList = new ArrayList<>();
        
        String actualMeetName = (meetName == null || meetName.trim().isEmpty()) ? extractMeetName(meetUrl) : meetName;
        String actualDate = (meetDate == null || meetDate.trim().isEmpty()) ? "TBD" : meetDate;

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

        for (int i = 0; i < meets.size(); i++) {
            MeetScraper.MeetInfo meet = meets.get(i);
            System.out.println("[BulkScrape] Processing meet " + (i + 1) + "/" + meets.size() + ": " + meet.name() + " (" + meet.date() + ")");
            try {
                totalSaved += bulkScrapeAndSaveRaw(meet.url(), meet.name(), meet.date());
            } catch (Exception e) {
                System.err.println("Error processing meet " + meet.name() + ": " + e.getMessage());
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
        RaceScraper raceScraper = new RaceScraper();

        // Fetch the meet page once
        Document doc;
        try {
            Thread.sleep(2000); // rate limit
            doc = Jsoup.connect(meetUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(15000)
                    .maxBodySize(0)
                    .get();
        } catch (IOException e) {
            System.err.println("[BulkScrape] Error fetching meet page: " + meetUrl + " - " + e.getMessage());
            return 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0;
        }

        double distance = raceScraper.detectRaceDistanceFromDoc(doc);
        List<Athlete> athletes = raceScraper.scrapeMensRaceFromDoc(doc, meetUrl);

        if (athletes.isEmpty()) {
            System.out.println("[BulkScrape] No athletes found at: " + meetUrl);
            return 0;
        }

        String actualMeetName = (meetName == null || meetName.trim().isEmpty()) ? extractMeetName(meetUrl) : meetName;
        String actualDate = (meetDate == null || meetDate.trim().isEmpty()) ? "TBD" : meetDate;

        // Check which results already exist for this meet to avoid duplicates
        java.util.Set<String> existingResultLinks = raceResultRepository.findByMeetName(actualMeetName)
                .stream().map(RaceResult::getAthleteLink).collect(Collectors.toSet());

        List<RaceResult> resultsToSave = new ArrayList<>();

        for (Athlete a : athletes) {
            if (a.link() != null && !a.link().isEmpty() && !existingResultLinks.contains(a.link())) {
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

        System.out.println("[BulkScrape] Saved " + resultsToSave.size() + " results from: " + actualMeetName);
        return resultsToSave.size();
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
}
