package com.example.service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.MeetScraper;
import com.example.RaceScraper;
import com.example.RaceScraper.Athlete;
import com.example.dto.AthleteProfileResponse;
import com.example.dto.AthleteRatingResponse;
import com.example.dto.MeetResponse;
import com.example.dto.RaceResultResponse;
import com.example.getCAF;
import com.example.priors;
import com.example.scrapePriors;

/**
 * Service layer that wraps the existing scraping and rating logic,
 * exposing clean methods for the REST controller.
 */
@Service
public class RaceService {

    @Value("${app.default-fatigue-coefficient:1.06}")
    private double defaultFatigueCoefficient;

    /**
     * Scrapes a meet page, calculates CAF, and returns fully-rated athletes.
     *
     * @param meetUrl             TFRRS meet URL
     * @return RaceResultResponse with CAF, distance, and athlete ratings
     */
    public RaceResultResponse scrapeAndRateRace(String meetUrl, Double fatigueCoefficient) {
        if (meetUrl == null || meetUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("meetUrl is required");
        }


        RaceScraper raceScraper = new RaceScraper();
        getCAF cafCalculator = new getCAF();

        // Detect race distance from the meet page
        double distance = raceScraper.detectRaceDistance(meetUrl);

        // Scrape athletes from the men's individual results
        List<Athlete> athletes = raceScraper.scrapeMensRace(meetUrl);
        if (athletes.isEmpty()) {
            return new RaceResultResponse(meetUrl, distance, 1.0, new ArrayList<>());
        }

        // Calculate CAF and rate each athlete
        List<getCAF.AthleteRating> ratings = cafCalculator.getCAF(athletes, distance);

        // Map internal records to API DTOs
        List<AthleteRatingResponse> dtoList = ratings.stream()
                .map(r -> new AthleteRatingResponse(r.name(), r.time(), r.link(), r.rating()))
                .collect(Collectors.toList());

        return new RaceResultResponse(meetUrl, distance, cafCalculator.lastCaf, dtoList);
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

        scrapePriors scraper = new scrapePriors();
        priors priorsCalculator = new priors();

        org.jsoup.nodes.Document doc = scraper.getAthleteDocument(athleteLink);
        if (doc == null) {
            throw new IllegalArgumentException("Could not fetch athlete page for: " + athleteLink);
        }

        String gender = scraper.getGender(doc);
        ArrayList<String> prs = scraper.getPRs(doc);
        double priorRating = priorsCalculator.getPriorRating(prs, gender);

        return new AthleteProfileResponse(gender, prs, priorRating);
    }
}
