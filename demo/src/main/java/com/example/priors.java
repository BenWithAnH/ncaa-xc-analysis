package com.example;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class priors {

    private static final List<String> EVENTS = Arrays.asList(
            "800m",
            "1500m",
            "Mile",
            "3000m",
            "5000m",
            "10000m");

    /**
     * Converts a time string (e.g. "1:56.54" or "31:25.74") to total seconds.
     * Returns 0.0 if the time is invalid, empty, or not run.
     */
    public static double parseTimeToSeconds(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return 0.0;
        }
        String clean = timeStr.trim().replace(",", "");
        if (clean.equalsIgnoreCase("dnf") || clean.equalsIgnoreCase("dns") || clean.equalsIgnoreCase("nt")) {
            return 0.0;
        }
        try {
            if (clean.contains(":")) {
                String[] parts = clean.split(":");
                if (parts.length == 2) {
                    double minutes = Double.parseDouble(parts[0]);
                    double seconds = Double.parseDouble(parts[1]);
                    return minutes * 60.0 + seconds;
                } else if (parts.length == 3) {
                    double hours = Double.parseDouble(parts[0]);
                    double minutes = Double.parseDouble(parts[1]);
                    double seconds = Double.parseDouble(parts[2]);
                    return hours * 3600.0 + minutes * 60.0 + seconds;
                }
            } else {
                return Double.parseDouble(clean);
            }
        } catch (NumberFormatException e) {
            // Log or ignore and return 0.0
        }
        return 0.0;
    }

    /**
     * Finds the coefficients file in the project.
     */
    private static File findCoefficientsFile() {
        String[] paths = {
                "src/main/iaaf-scoring-tables-master/iaaf-scoring-tables-master/coefficients-2025.json",
                "demo/src/main/iaaf-scoring-tables-master/iaaf-scoring-tables-master/coefficients-2025.json",
                "../demo/src/main/iaaf-scoring-tables-master/iaaf-scoring-tables-master/coefficients-2025.json",
                "coefficients-2025.json"
        };
        for (String path : paths) {
            File f = new File(path);
            if (f.exists()) {
                return f;
            }
        }
        // Recursive fallback search
        File projectDir = new File(".");
        File found = searchFile(projectDir, "coefficients-2025.json");
        if (found != null) {
            return found;
        }
        return null;
    }

    private static File searchFile(File dir, String filename) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return null;
        }
        File[] files = dir.listFiles();
        if (files == null)
            return null;
        for (File f : files) {
            if (f.isDirectory()) {
                if (f.getName().equals("target") || f.getName().equals(".git") || f.getName().equals(".mvn")) {
                    continue;
                }
                File found = searchFile(f, filename);
                if (found != null)
                    return found;
            } else if (f.getName().equals(filename)) {
                return f;
            }
        }
        return null;
    }

    /**
     * Calculates IAAF points using the quadratic formula: points = a * mark^2 + b *
     * mark + c
     */
    public static long calculatePoints(double seconds, List<Double> coefs) {
        if (seconds <= 0.0 || coefs == null || coefs.size() < 3) {
            return 0;
        }
        double a = coefs.get(0);
        double b = coefs.get(1);
        double c = coefs.get(2);

        double pointsVal = a * seconds * seconds + b * seconds + c;
        long points = Math.round(pointsVal);
        return Math.max(0, points);
    }

    /**
     * Calculates points for the given PRs and a specific gender.
     */
    public void findBest(ArrayList<String> prs, String gender) {
        File coeffFile = findCoefficientsFile();
        if (coeffFile == null) {
            System.err.println("Error: coefficients-2025.json file not found.");
            return;
        }

        try (Reader reader = new FileReader(coeffFile)) {
            Gson gson = new Gson();
            Type type = new TypeToken<Map<String, Map<String, List<Double>>>>() {
            }.getType();
            Map<String, Map<String, List<Double>>> coefficients = gson.fromJson(reader, type);

            Map<String, List<Double>> genderCoefs = coefficients.get(gender.toLowerCase());
            if (genderCoefs == null) {
                System.err.println("Error: Gender '" + gender + "' not found in coefficients.");
                return;
            }

            System.out.println("\n--------------------------------------------------------------");
            System.out.println(" IAAF Points for " + gender.toUpperCase() + "'s Events:");
            System.out.println("--------------------------------------------------------------");
            System.out.printf(" %-12s | %-12s | %-12s | %-12s\n", "Event", "Mark", "Seconds", "IAAF Points");
            System.out.println("--------------------------------------------------------------");

            String bestEvent = "N/A";
            long maxPoints = -1;
            String bestMark = "N/A";

            for (int i = 0; i < Math.min(prs.size(), EVENTS.size()); i++) {
                String eventName = EVENTS.get(i);
                String mark = prs.get(i);
                double seconds = parseTimeToSeconds(mark);

                if (seconds > 0) {
                    List<Double> coefs = genderCoefs.get(eventName);
                    long points = calculatePoints(seconds, coefs);
                    System.out.printf(" %-12s | %-12s | %-12.2f | %-12d\n", eventName, mark, seconds, points);

                    if (points > maxPoints) {
                        maxPoints = points;
                        bestEvent = eventName;
                        bestMark = mark;
                    }
                } else {
                    System.out.printf(" %-12s | %-12s | %-12s | %-12s\n", eventName, (mark == null ? "N/A" : mark),
                            "N/A", "N/A");
                }
            }
            System.out.println("--------------------------------------------------------------");
            if (maxPoints >= 0) {
                System.out.printf(" Best Performance: %s in %s (%d points)\n", bestMark, bestEvent, maxPoints);
            } else {
                System.out.println(" No valid performances found.");
            }
            System.out.println("--------------------------------------------------------------");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Calculates and returns the highest IAAF points value across the athlete's
     * events
     * as the prior rating.
     */
    public double getPriorRating(ArrayList<String> prs, String gender) {
        if (prs == null || prs.isEmpty() || gender == null) {
            return 0.0;
        }
        File coeffFile = findCoefficientsFile();
        if (coeffFile == null) {
            System.err.println("Error: coefficients-2025.json file not found.");
            return 0.0;
        }

        try (Reader reader = new FileReader(coeffFile)) {
            Gson gson = new Gson();
            Type type = new TypeToken<Map<String, Map<String, List<Double>>>>() {
            }.getType();
            Map<String, Map<String, List<Double>>> coefficients = gson.fromJson(reader, type);

            Map<String, List<Double>> genderCoefs = coefficients.get(gender.toLowerCase());
            if (genderCoefs == null) {
                System.err.println("Error: Gender '" + gender + "' not found in coefficients.");
                return 0.0;
            }

            List<Double> allPoints = new ArrayList<>();

            for (int i = 0; i < Math.min(prs.size(), EVENTS.size()); i++) {
                String eventName = EVENTS.get(i);
                String mark = prs.get(i);
                double seconds = parseTimeToSeconds(mark);

                if (seconds > 0) {
                    List<Double> coefs = genderCoefs.get(eventName);
                    long points = calculatePoints(seconds, coefs);
                    if (points > 0) {
                        allPoints.add((double) points);
                    }
                }
            }

            if (!allPoints.isEmpty()) {
                allPoints.sort((a, b) -> Double.compare(b, a));
                int k = (int) Math.ceil(allPoints.size() * 2.0 / 3.0);
                double sum = 0.0;
                for (int i = 0; i < k; i++) {
                    sum += allPoints.get(i);
                }
                return sum / k;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0.0;
    }

}
