package com.example;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.dto.MeetRatingInfo;
import com.example.dto.RaceResultResponse;
import com.example.entity.Athlete;
import com.example.service.RaceService;

@SpringBootTest
public class RunPriorRatingsDemoTest {

    @Autowired
    private RaceService raceService;

    @Test
    void runPriorRatings() {
        String url = "https://www.tfrrs.org/results/xc/27301/NCAA_Division_I_Cross_Country_Championships";
        System.out.println("=== 1. SCRAPING, RATING & PERSISTING MEET TO POSTGRES ===");
        System.out.println("Target Meet URL: " + url);

        long startScrape = System.currentTimeMillis();
        RaceResultResponse raceResponse = raceService.scrapeAndRateRace(url, "NCAA Division I Cross Country Championships", "Nov 18, 2023", true);
        long durationScrape = System.currentTimeMillis() - startScrape;

        System.out.println("Saved/Updated " + raceResponse.getAthletes().size() + " race results/athletes in Postgres in " + durationScrape + " ms.");
        System.out.println("Course Adjustment Factor (CAF): " + raceResponse.getCaf());

        System.out.println("\n=== 1b. ATHLETE TRACK BASELINE (PR PRIORS) vs. RACE RATING ===");
        for (int i = 0; i < Math.min(10, raceResponse.getAthletes().size()); i++) {
            com.example.dto.AthleteRatingResponse a = raceResponse.getAthletes().get(i);
            System.out.printf("  #%d %s - Time: %s | Track Baseline (PR Prior): %.2f | Race Rating: %.2f%n",
                    i + 1, a.getName(), a.getTime(), a.getPriorRating(), a.getRating());
        }

        System.out.println("\n=== 2. RETRIEVING PRIOR RATINGS FOR MEET ATHLETES ===");
        long startPrior = System.currentTimeMillis();
        Map<String, List<MeetRatingInfo>> results = raceService.getPriorRatingsForMeetAthletes(url);
        long durationPrior = System.currentTimeMillis() - startPrior;

        System.out.println("Finished getPriorRatingsForMeetAthletes in " + durationPrior + " ms.");
        System.out.println("Total Athletes in Meet: " + results.size());

        int count = 0;
        for (Map.Entry<String, List<MeetRatingInfo>> entry : results.entrySet()) {
            count++;
            if (count <= 15) { // Print first 15 for concise inspection
                System.out.println("\n[" + count + "] Athlete: " + entry.getKey());
                List<MeetRatingInfo> meetList = entry.getValue();
                if (meetList == null || meetList.isEmpty()) {
                    System.out.println("    No prior meet ratings found.");
                } else {
                    for (MeetRatingInfo info : meetList) {
                        System.out.printf("    - Meet: %s | Date: %s | Time: %s | Rating: %s | Link: %s%n",
                                info.meetName(), info.raceDate(), info.raceTime(), info.rating(), info.athleteLink());
                    }
                }
            }
        }
        if (results.size() > 15) {
            System.out.println("\n... and " + (results.size() - 15) + " more athletes.");
        }

        System.out.println("\n=== 3. VERIFYING TOP ATHLETES IN POSTGRES TABLE ===");
        List<Athlete> topAthletes = raceService.getTopAthletes();
        System.out.println("Total Top Athletes in Database: " + topAthletes.size());
        for (int i = 0; i < Math.min(10, topAthletes.size()); i++) {
            Athlete a = topAthletes.get(i);
            System.out.printf("  #%d %s - Rating: %.2f | Best Time: %s | Link: %s%n",
                    i + 1, a.getName(), a.getRating(), a.getBestTime(), a.getLink());
        }

        System.out.println("\n=== COMPLETED SUCCESSFULLY ===");
    }
}
