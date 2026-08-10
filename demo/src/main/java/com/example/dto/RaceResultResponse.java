package com.example.dto;

import java.util.List;


public class RaceResultResponse {

    private String meetUrl;
    private double distance;
    private double caf;
    private List<AthleteRatingResponse> athletes;

    public RaceResultResponse() {
    }

    public RaceResultResponse(String meetUrl, double distance, double caf,
            List<AthleteRatingResponse> athletes) {
        this.meetUrl = meetUrl;
        this.distance = distance;
        this.caf = caf;
        this.athletes = athletes;
    }

    public String getMeetUrl() {
        return meetUrl;
    }

    public void setMeetUrl(String meetUrl) {
        this.meetUrl = meetUrl;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public double getCaf() {
        return caf;
    }

    public void setCaf(double caf) {
        this.caf = caf;
    }

    public List<AthleteRatingResponse> getAthletes() {
        return athletes;
    }

    public void setAthletes(List<AthleteRatingResponse> athletes) {
        this.athletes = athletes;
    }
}
