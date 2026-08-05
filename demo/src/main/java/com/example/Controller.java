package com.example;

import java.util.List;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.dto.AthleteProfileResponse;
import com.example.dto.AthleteRatingResponse;
import com.example.dto.MeetResponse;
import com.example.dto.RaceRequest;
import com.example.dto.RaceResultResponse;
import com.example.service.RaceService;

@RestController
@CrossOrigin
@RequestMapping("/api")
public class Controller {

    private final RaceService raceService;

    public Controller(RaceService raceService) {
        this.raceService = raceService;
    }

    /**
     * Scrapes a meet, calculates the Course Adjustment Factor (CAF),
     * and returns rated athletes.
     *
     * POST /api/race/rate
     * Body: { "meetUrl": "https://tfrrs.org/results/xc/26324/Jasper_Fall_XC_Invitational", "fatigueCoefficient": 1.06 }
     */
    @PostMapping("/race/rate")
    public RaceResultResponse rateRace(@RequestBody RaceRequest request) {
        return raceService.scrapeAndRateRace(
                request.getMeetUrl(),
                request.getFatigueCoefficient());
    }

    /**
     * Scrapes raw race results (names, times, links) without rating.
     *
     * GET /api/race/results?meetUrl=https://...
     */
    @GetMapping("/race/results")
    public List<AthleteRatingResponse> getRaceResults(@RequestParam String meetUrl) {
        return raceService.scrapeRaceResults(meetUrl);
    }

    /**
     * Lists available XC meets from TFRRS.
     *
     * GET /api/meets?maxPages=1
     */
    @GetMapping("/meets")
    public List<MeetResponse> listMeets(@RequestParam(defaultValue = "1") int maxPages) {
        return raceService.listXCMeets(maxPages);
    }

    /**
     * Fetches an athlete's PRs and prior rating from their TFRRS profile.
     *
     * GET /api/athlete/profile?link=https://...
     */
    @GetMapping("/athlete/profile")
    public AthleteProfileResponse getAthleteProfile(@RequestParam String link) {
        return raceService.getAthleteProfile(link);
    }
}