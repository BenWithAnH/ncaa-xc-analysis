package com.example;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import com.example.RaceScraper.Athlete;
import org.jsoup.nodes.Document;

// error handling, maybe implement a
// big scraper part (every meet in a given list)

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class getCAF {

    // 1. CONSTANTS
    public static final double BASE_SCORE = 1000.0;
    private static final double RATING_CURVE_EXPONENT = 0.5;
    private static final int RATE_LIMIT_MS = 50;

    public record AthleteRating(String name, String time, String link, double rating) {
    }

    public final List<Double> ratings = Collections.synchronizedList(new ArrayList<>());
    public double lastCaf = 1.0;

    public ArrayList<AthleteRating> getCAF(List<Athlete> athletes, double raceDistanceMeters,
            double fatigueCoefficient) {
        ratings.clear();

        if (athletes == null || athletes.isEmpty()) {
            return new ArrayList<>();
        }

        scrapePriors scraper = new scrapePriors();
        priors priorsCalculator = new priors();

        List<Double> validRatios = Collections.synchronizedList(new ArrayList<>());

        List<CompletableFuture<Void>> futures = athletes.stream()
                .map(athlete -> CompletableFuture.runAsync(() -> processAthlete(athlete, raceDistanceMeters,
                        fatigueCoefficient, scraper, priorsCalculator, validRatios)))
                .collect(Collectors.toList());

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        this.lastCaf = calculateTrimmedAverageRatio(validRatios);

        // 4. CLEAN OUTPUT USING RECORD AND EXPLICIT ARRAYLIST
        ArrayList<AthleteRating> athleteRatingsList = new ArrayList<>();

        for (Athlete athlete : athletes) {
            double actualTime = priors.parseTimeToSeconds(athlete.time());

            if (actualTime > 0.0) {
                double adjustedTime = actualTime / this.lastCaf;
                double rating = calculateRacePoints(actualTime, this.lastCaf, raceDistanceMeters, raceDistanceMeters,
                        "M", fatigueCoefficient);

                // Instantiate the record and add it directly to the ArrayList
                athleteRatingsList.add(new AthleteRating(athlete.name(), athlete.time(), athlete.link(), rating));
            }
        }

        return athleteRatingsList;
    }

    private void processAthlete(Athlete athlete, double raceDistanceMeters, double fatigueCoefficient,
            scrapePriors scraper, priors priorsCalculator, List<Double> validRatios) {
        try {
            Thread.sleep(RATE_LIMIT_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        Document doc = scraper.getAthleteDocument(athlete.link());
        String gender = scraper.getGender(doc);
        ArrayList<String> prs = scraper.getPRs(doc);

        double priorRating = priorsCalculator.getPriorRating(prs, gender);
        if (priorRating > 0.0) {
            ratings.add(priorRating);
        }

        double actualTime = priors.parseTimeToSeconds(athlete.time());

        if (priorRating > 0.0 && actualTime > 0.0) {
            double unadjustedRaceRating = calculateRacePoints(
                    actualTime, 1.0, raceDistanceMeters, raceDistanceMeters, gender, fatigueCoefficient);

            if (unadjustedRaceRating > 0.0) {
                validRatios.add(priorRating / unadjustedRaceRating);
            }
        }
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
            double targetTrackDistance, String gender, double fatigueCoefficient) {

        double normalizedXcTime = actualTime / caf;
        double equivalentTrackTime = normalizedXcTime
                * Math.pow(targetTrackDistance / xcDistanceMeters, fatigueCoefficient);

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
