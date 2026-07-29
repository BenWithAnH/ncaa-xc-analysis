package com.example;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.LinkedHashMap;
import com.example.parseRace.Athlete;
import org.jsoup.nodes.Document;

public class getCAF {

    public static final double BASE_SCORE = 1000.0;
    // Todo: save the ratings to an output file, error handling, maybe implement a
    // big scraper part (every meet in a given list)
    List<Double> ratings = Collections.synchronizedList(new ArrayList<>());
    public double lastCaf = 1.0;

    public ArrayList<ArrayList<Object>> getCAF(List<Athlete> athletes, double raceDistanceMeters) {
        return getCAF(athletes, raceDistanceMeters, 1.06);
    }

    public ArrayList<ArrayList<Object>> getCAF(List<Athlete> athletes, double raceDistanceMeters,
            double fatigueCoefficient) {
        ratings.clear();

        if (athletes == null || athletes.isEmpty()) {
            return new ArrayList<>();
        }

        scrapePriors scraper = new scrapePriors();
        priors priorsCalculator = new priors();

        double ratingCurveExponent = 0.5;
        List<Double> validRatios = Collections.synchronizedList(new ArrayList<>());

        List<CompletableFuture<Void>> futures = athletes.stream()
                .map(athlete -> CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
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
                        double targetTrackDistance = raceDistanceMeters;

                        double unadjustedRaceRating = calculateRacePoints(actualTime, 1.0, raceDistanceMeters,
                                targetTrackDistance, gender, fatigueCoefficient);

                        if (unadjustedRaceRating > 0.0) {
                            validRatios.add(priorRating / unadjustedRaceRating);
                        }
                    }
                }))
                .collect(Collectors.toList());

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        double caf = 1.0;
        if (!validRatios.isEmpty()) {
            List<Double> sortedRatios = new ArrayList<>(validRatios);
            Collections.sort(sortedRatios);

            int n = sortedRatios.size();
            int trimCount = 0;
            if (n >= 5) {
                trimCount = (int) (n * 0.1);
            }

            double sum = 0.0;
            int count = 0;
            for (int i = trimCount; i < n - trimCount; i++) {
                sum += sortedRatios.get(i);
                count++;
            }

            double averageRatio = count > 0 ? (sum / count) : 1.0;
            caf = Math.pow(averageRatio, ratingCurveExponent);
        }
        this.lastCaf = caf;

        ArrayList<ArrayList<Object>> athleteRatingsList = new ArrayList<>();
        for (Athlete athlete : athletes) {
            double actualTime = priors.parseTimeToSeconds(athlete.time());
            if (actualTime > 0.0) {
                double adjustedTime = actualTime / caf;
                double rating = adjustedTime / 10.0;
                ArrayList<Object> athleteInfo = new ArrayList<>();
                athleteInfo.add(athlete.name());
                athleteInfo.add(athlete.time());
                athleteInfo.add(athlete.link());
                athleteInfo.add(rating);
                athleteRatingsList.add(athleteInfo);
            }
        }

        return athleteRatingsList;
    }

    public double calculateRacePoints(double actualTime, double caf, double xcDistanceMeters,
            double targetTrackDistance, String gender, double fatigueCoefficient) {
        double normalizedXcTime = actualTime / caf;

        double equivalentTrackTime = normalizedXcTime
                * Math.pow(targetTrackDistance / xcDistanceMeters, fatigueCoefficient);

        double trackBaselineSeconds = getBaselineSeconds(targetTrackDistance, gender);
        double ratingCurveExponent = 0.5;

        return BASE_SCORE * Math.pow(trackBaselineSeconds / equivalentTrackTime, 1.0 / ratingCurveExponent);
    }

    private double getBaselineSeconds(double distanceMeters, String gender) {
        boolean isMale = gender.equalsIgnoreCase("M") || gender.equalsIgnoreCase("men");
        boolean isFemale = gender.equalsIgnoreCase("F") || gender.equalsIgnoreCase("women");
        if (isMale && distanceMeters == 8000.0) {
            return 1440.0; // 24:00
        } else if (isFemale && distanceMeters == 6000.0) {
            return 1260.0; // 21:00
        } else if (isMale && distanceMeters == 10000.0) {
            return 1800.0; // 30:00
        } else if (isFemale && distanceMeters == 5000.0) {
            return 1050.0; // 17:30
        }

        return (distanceMeters / 1000.0) * 190.0;
    }

    public static void main(String[] args) {
        parseRace p = new parseRace();
        List<Athlete> athletes = p
                .getCAF("https://www.tfrrs.org/results/xc/26950/2025_MAAC_Cross_Country_Championships");
        if (athletes != null && !athletes.isEmpty()) {
            System.out.println("Parsed " + athletes.size() + " athletes.");
            // small sample for testing
            List<Athlete> subList = athletes.subList(0, Math.min(25, athletes.size()));
            System.out.println("Testing with first " + subList.size() + " athletes:");
            for (Athlete a : subList) {
                System.out.println("  Name: " + a.name() + ", Time: " + a.time() + ", Link: " + a.link());
            }
            getCAF g = new getCAF();
            ArrayList<ArrayList<Object>> athleteRatings = g.getCAF(subList, 8000);
            System.out.println("Calculated CAF: " + g.lastCaf);
            System.out.println("Athlete Ratings:");
            for (ArrayList<Object> row : athleteRatings) {
                System.out.println("  Name: " + row.get(0) + ", Time: " + row.get(1) + ", Link: " + row.get(2)
                        + ", Rating: " + row.get(3));
            }
            System.out.println("Stored Prior Ratings: " + g.ratings); // best track rating using point system
        } else {
            System.out.println("Failed to parse athletes from race.");
        }
    }
}
