package com.example;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import com.example.RaceScraper.Athlete;
import com.example.repository.AthleteRepository;
import org.jsoup.nodes.Document;
import java.util.Optional;

public class getCAF {

    // 1. CONSTANTS
    public static final double BASE_SCORE = 1000.0;
    private static final double RATING_CURVE_EXPONENT = 0.5;
    private static final int RATE_LIMIT_MS = 50;

    public record AthleteRating(String name, String time, String link, double rating, double priorRating) {
    }

    public final List<Double> ratings = Collections.synchronizedList(new ArrayList<>());
    public double lastCaf = 1.0;

    private final AthleteRepository athleteRepository;

    public getCAF(AthleteRepository athleteRepository) {
        this.athleteRepository = athleteRepository;
    }

    public ArrayList<AthleteRating> getCAF(List<Athlete> athletes, double raceDistanceMeters) {
        ratings.clear();

        if (athletes == null || athletes.isEmpty()) {
            return new ArrayList<>();
        }

        scrapePriors scraper = new scrapePriors();
        priors priorsCalculator = new priors();

        List<Double> validRatios = Collections.synchronizedList(new ArrayList<>());
        List<AthleteRating> preRatings = Collections.synchronizedList(new ArrayList<>());

        List<CompletableFuture<Void>> futures = athletes.stream()
                .map(athlete -> CompletableFuture.runAsync(() -> processAthlete(athlete, raceDistanceMeters, scraper, priorsCalculator, validRatios, preRatings)))
                .collect(Collectors.toList());

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        this.lastCaf = calculateTrimmedAverageRatio(validRatios);

        // 4. CLEAN OUTPUT USING RECORD AND EXPLICIT ARRAYLIST
        ArrayList<AthleteRating> athleteRatingsList = new ArrayList<>();

        for (AthleteRating preRating : preRatings) {
            double actualTime = priors.parseTimeToSeconds(preRating.time());

            if (actualTime > 0.0) {
                double adjustedTime = actualTime / this.lastCaf;
                double rating = calculateRacePoints(actualTime, this.lastCaf, raceDistanceMeters, raceDistanceMeters, "M");

                athleteRatingsList.add(new AthleteRating(preRating.name(), preRating.time(), preRating.link(), rating, preRating.priorRating()));
            }
        }

        athleteRatingsList.sort((a, b) -> Double.compare(b.rating(), a.rating()));
        return athleteRatingsList;
    }

    private void processAthlete(Athlete athlete, double raceDistanceMeters, scrapePriors scraper, priors priorsCalculator, List<Double> validRatios, List<AthleteRating> preRatings) {
        double priorRating = 0.0;
        
        // 1. Check cache first!
        if (athlete.link() != null && !athlete.link().isEmpty()) {
            Optional<com.example.entity.Athlete> cachedAthlete = athleteRepository.findById(athlete.link());
            if (cachedAthlete.isPresent() && cachedAthlete.get().getRating() != null && cachedAthlete.get().getRating() > 0) {
                priorRating = cachedAthlete.get().getRating();
            }
        }

        // 2. If not in cache, scrape it
        if (priorRating == 0.0 && athlete.link() != null && !athlete.link().isEmpty()) {
            try {
                Thread.sleep(RATE_LIMIT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }

            Document doc = scraper.getAthleteDocument(athlete.link());
            if (doc != null) {
                String gender = scraper.getGender(doc);
                ArrayList<String> prs = scraper.getPRs(doc);
                priorRating = priorsCalculator.getPriorRating(prs, gender);

                // Save to cache
                if (priorRating > 0.0) {
                    com.example.entity.Athlete newAthlete = new com.example.entity.Athlete(athlete.link(), athlete.name(), priorRating);
                    athleteRepository.save(newAthlete);
                }
            }
        }

        if (priorRating > 0.0) {
            ratings.add(priorRating);
        }

        double actualTime = priors.parseTimeToSeconds(athlete.time());

        if (priorRating > 0.0 && actualTime > 0.0) {
            double unadjustedRaceRating = calculateRacePoints(
                    actualTime, 1.0, raceDistanceMeters, raceDistanceMeters, "M"); // Simplified gender for now

            if (unadjustedRaceRating > 0.0) {
                validRatios.add(priorRating / unadjustedRaceRating);
            }
        }

        preRatings.add(new AthleteRating(athlete.name(), athlete.time(), athlete.link(), 0.0, priorRating));
    }

    private double calculateTrimmedAverageRatio(List<Double> ratios) {
        if (ratios.isEmpty()) {
            return 1.0;
        }

        List<Double> sortedRatios = new ArrayList<>(ratios);
        Collections.sort(sortedRatios);

        int n = sortedRatios.size();
        int trimCount = (n >= 5) ? (int) (n * 0.1) : 0;

        double sum = 0.0;
        int count = 0;

        for (int i = trimCount; i < n - trimCount; i++) {
            sum += sortedRatios.get(i);
            count++;
        }

        double averageRatio = (count > 0) ? (sum / count) : 1.0;
        return Math.pow(averageRatio, RATING_CURVE_EXPONENT);
    }

    public double calculateRacePoints(double actualTime, double caf, double xcDistanceMeters,
            double targetTrackDistance, String gender) {

        double normalizedXcTime = actualTime / caf;
        double equivalentTrackTime = normalizedXcTime
                * Math.pow(targetTrackDistance / xcDistanceMeters, 1.06);

        double trackBaselineSeconds = getBaselineSeconds(targetTrackDistance, gender);

        return BASE_SCORE * Math.pow(trackBaselineSeconds / equivalentTrackTime, 1.0 / RATING_CURVE_EXPONENT);
    }

    private double getBaselineSeconds(double distanceMeters, String gender) {
        boolean isMale = gender.equalsIgnoreCase("M") || gender.equalsIgnoreCase("men");
        boolean isFemale = gender.equalsIgnoreCase("F") || gender.equalsIgnoreCase("women");

        // baseline race times (in seconds)
        if (isMale && distanceMeters == 8000.0)
            return 1440.0;
        if (isFemale && distanceMeters == 6000.0)
            return 1260.0;
        if (isMale && distanceMeters == 10000.0)
            return 1800.0;
        if (isFemale && distanceMeters == 5000.0)
            return 1050.0;

        return (distanceMeters / 1000.0) * 190.0;
    }
}
