package com.example;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PriorsTest {

    @Test
    void testGetPriorRatingAveragesTopThreeEvents() {
        Priors priors = new Priors();

        // 6 events: 800m, 1500m, Mile, 3000m, 5000m, 10000m
        ArrayList<String> prs = new ArrayList<>(Arrays.asList(
                "1:45.00", // 800m
                "3:35.00", // 1500m
                "3:55.00", // Mile
                "7:40.00", // 3000m
                "13:10.00", // 5000m
                "27:30.00"  // 10000m
        ));

        double rating = priors.getPriorRating(prs, "men");
        assertTrue(rating > 0.0, "Rating should be positive");

        // Compute points for each event individually to confirm it matches top 3 average
        java.io.Reader reader = Priors.getCoefficientsReader();
        com.google.gson.Gson gson = new com.google.gson.Gson();
        java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<java.util.Map<String, java.util.Map<String, java.util.List<Double>>>>() {}.getType();
        java.util.Map<String, java.util.Map<String, java.util.List<Double>>> coefficients = gson.fromJson(reader, type);
        java.util.Map<String, java.util.List<Double>> menCoefs = coefficients.get("men");

        java.util.List<String> events = Arrays.asList("800m", "1500m", "Mile", "3000m", "5000m", "10000m");
        java.util.List<Double> points = new ArrayList<>();
        for (int i = 0; i < prs.size(); i++) {
            double sec = Priors.parseTimeToSeconds(prs.get(i));
            long pts = Priors.calculatePoints(sec, menCoefs.get(events.get(i)), events.get(i));
            points.add((double) pts);
        }
        points.sort((a, b) -> Double.compare(b, a));

        double expectedTop3Average = (points.get(0) + points.get(1) + points.get(2)) / 3.0;
        assertEquals(expectedTop3Average, rating, 0.001);
    }

    @Test
    void testFiveAndTenKPointBias() {
        java.io.Reader reader = Priors.getCoefficientsReader();
        com.google.gson.Gson gson = new com.google.gson.Gson();
        java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<java.util.Map<String, java.util.Map<String, java.util.List<Double>>>>() {}.getType();
        java.util.Map<String, java.util.Map<String, java.util.List<Double>>> coefficients = gson.fromJson(reader, type);
        java.util.Map<String, java.util.List<Double>> menCoefs = coefficients.get("men");

        // 5000m: 13:10 (790s)
        double sec5k = 790.0;
        long base5k = Priors.calculatePoints(sec5k, menCoefs.get("5000m"));
        long biased5k = Priors.calculatePoints(sec5k, menCoefs.get("5000m"), "5000m");
        assertEquals(Math.round(base5k * 1.05), biased5k);

        // 10000m: 27:30 (1650s)
        double sec10k = 1650.0;
        long base10k = Priors.calculatePoints(sec10k, menCoefs.get("10000m"));
        long biased10k = Priors.calculatePoints(sec10k, menCoefs.get("10000m"), "10000m");
        assertEquals(Math.round(base10k * 1.05), biased10k);

        // 3000m: Should NOT be biased
        double sec3k = 460.0;
        long base3k = Priors.calculatePoints(sec3k, menCoefs.get("3000m"));
        long unbiased3k = Priors.calculatePoints(sec3k, menCoefs.get("3000m"), "3000m");
        assertEquals(base3k, unbiased3k);
    }

    @Test
    void testGetPriorRatingWithFewerThanThreeEvents() {
        Priors priors = new Priors();

        // Athlete with only 2 PR marks: 800m and 1500m
        ArrayList<String> prs = new ArrayList<>(Arrays.asList(
                "1:50.00",
                "3:45.00"
        ));

        double rating = priors.getPriorRating(prs, "men");
        assertTrue(rating > 0.0);

        java.io.Reader reader = Priors.getCoefficientsReader();
        com.google.gson.Gson gson = new com.google.gson.Gson();
        java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<java.util.Map<String, java.util.Map<String, java.util.List<Double>>>>() {}.getType();
        java.util.Map<String, java.util.Map<String, java.util.List<Double>>> coefficients = gson.fromJson(reader, type);
        java.util.Map<String, java.util.List<Double>> menCoefs = coefficients.get("men");

        double pt1 = Priors.calculatePoints(Priors.parseTimeToSeconds("1:50.00"), menCoefs.get("800m"), "800m");
        double pt2 = Priors.calculatePoints(Priors.parseTimeToSeconds("3:45.00"), menCoefs.get("1500m"), "1500m");
        double expectedAvg = (pt1 + pt2) / 2.0;

        assertEquals(expectedAvg, rating, 0.001);
    }
}
