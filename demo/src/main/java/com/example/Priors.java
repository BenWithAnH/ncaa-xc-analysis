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

public class Priors {

    private static final List<String> EVENTS = Arrays.asList(
            "800m",
            "1500m",
            "Mile",
            "3000m",
            "5000m",
            "10000m");


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
            //log?
        }
        return 0.0;
    }


    private static File findCoefficientsFile() {
        String[] paths = {
                "src/main/resources/coefficients-2025.json",
                "demo/src/main/resources/coefficients-2025.json",
                "coefficients-2025.json"
        };
        for (String path : paths) {
            File f = new File(path);
            if (f.exists()) {
                return f;
            }
        }

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


    public void findBest(ArrayList<String> prs, String gender) {
        File coeffFile = findCoefficientsFile();
        if (coeffFile == null) {
            System.err.println("coef file not found.");
            return;
        }

        try (Reader reader = new FileReader(coeffFile)) {
            Gson gson = new Gson();
            Type type = new TypeToken<Map<String, Map<String, List<Double>>>>() {
            }.getType();
            Map<String, Map<String, List<Double>>> coefficients = gson.fromJson(reader, type);

            Map<String, List<Double>> genderCoefs = coefficients.get(gender.toLowerCase());
            if (genderCoefs == null) {
                return;
            }

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

                    if (points > maxPoints) {
                        maxPoints = points;
                        bestEvent = eventName;
                        bestMark = mark;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public double getPriorRating(ArrayList<String> prs, String gender) {
        if (prs == null || prs.isEmpty() || gender == null) {
            return 0.0;
        }
        File coeffFile = findCoefficientsFile();
        if (coeffFile == null) {
            System.err.println("coef file not found.");
            return 0.0;
        }

        try (Reader reader = new FileReader(coeffFile)) {
            Gson gson = new Gson();
            Type type = new TypeToken<Map<String, Map<String, List<Double>>>>() {
            }.getType();
            Map<String, Map<String, List<Double>>> coefficients = gson.fromJson(reader, type);

            Map<String, List<Double>> genderCoefs = coefficients.get(gender.toLowerCase());
            if (genderCoefs == null) {
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
