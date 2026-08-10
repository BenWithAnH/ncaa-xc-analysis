package com.example.service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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

    @Value("${app.default-fatigue-coefficient:1.06}")
    private double defaultFatigueCoefficient;

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
    public RaceResultResponse scrapeAndRateRace(String meetUrl, Double fatigueCoefficient) {
        if (meetUrl == null || meetUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("meetUrl is required");
        }

        RaceScraper raceScraper = new RaceScraper();
        GetCAF cafCalculator = new GetCAF(athleteRepository);

        double distance = raceScraper.detectRaceDistance(meetUrl);

        List<Athlete> athletes = raceScraper.scrapeMensRace(meetUrl);
        if (athletes.isEmpty()) {
            return new RaceResultResponse(meetUrl, distance, 1.0, new ArrayList<>());
        }

        List<GetCAF.AthleteRating> ratings = cafCalculator.getCAF(athletes, distance);

        List<AthleteRatingResponse> dtoList = new ArrayList<>();
        
        String meetName = extractMeetName(meetUrl);
        String dummyDate = "TBD"; 

        for (GetCAF.AthleteRating r : ratings) {
            dtoList.add(new AthleteRatingResponse(r.name(), r.time(), r.link(), r.rating()));
            
            if (r.link() != null && !r.link().isEmpty()) {
                RaceResult result = new RaceResult(
                        r.link(),
                        r.name(),
                        meetName,
                        dummyDate,
                        r.time(),
                        r.rating() 
                );
                raceResultRepository.save(result);

                com.example.entity.Athlete athlete = athleteRepository.findById(r.link()).orElse(null);
                if (athlete == null) {
                    athlete = new com.example.entity.Athlete(r.link(), r.name(), r.rating());
                    athlete.setBestTime(r.time());
                    athleteRepository.save(athlete);
                } else {
                    String currentBest = athlete.getBestTime();
                    
                    if (currentBest == null || currentBest.isEmpty()) {
                        athlete.setBestTime(r.time());
                        athlete.setRating(r.rating());
                        athleteRepository.save(athlete);
                    } else {
                        double currentBestSeconds = Priors.parseTimeToSeconds(currentBest);
                        double newSeconds = Priors.parseTimeToSeconds(r.time());
                        boolean updated = false;
                        
                        if (newSeconds > 0 && newSeconds < currentBestSeconds) {
                            athlete.setBestTime(r.time());
                            updated = true;
                        }
                        
                        double currentRating = athlete.getRating() != null ? athlete.getRating() : 0.0;
                        if (r.rating() > currentRating) {
                            athlete.setRating(r.rating());
                            updated = true;
                        }
                        
                        if (updated) {
                            athleteRepository.save(athlete);
                        }
                    }
                }
            }
        }

        return new RaceResultResponse(meetUrl, distance, cafCalculator.lastCaf, dtoList);
    }
    
    private String extractMeetName(String url) {
        if (url == null) return "Unknown Meet";
        String[] parts = url.split("/");
        return parts[parts.length - 1].replace("_", " ");
    }


    public int bulkScrapeMeets(int maxPages) {
        if (maxPages < 1) {
            throw new IllegalArgumentException("maxPages must be at least 1");
        }

        MeetScraper meetScraper = new MeetScraper();
        List<MeetScraper.MeetInfo> meets = meetScraper.scrapeXCMeets(maxPages);
        int totalSaved = 0;

        for (MeetScraper.MeetInfo meet : meets) {
            System.out.println("Processing meet: " + meet.name());
            try {
                RaceResultResponse response = scrapeAndRateRace(meet.url(), defaultFatigueCoefficient);
                // Scrape and rate race already saves RaceResult entities!
                totalSaved += response.getAthletes().size();
            } catch (Exception e) {
                System.err.println("Error processing meet " + meet.name() + ": " + e.getMessage());
            }
        }
        
        return totalSaved;
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
