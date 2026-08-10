package com.example.dto;


public class RaceRequest {

    private String meetUrl;
    private Double fatigueCoefficient;

    public RaceRequest() {
    }

    public RaceRequest(String meetUrl, Double fatigueCoefficient) {
        this.meetUrl = meetUrl;
        this.fatigueCoefficient = fatigueCoefficient;
    }

    public String getMeetUrl() {
        return meetUrl;
    }

    public void setMeetUrl(String meetUrl) {
        this.meetUrl = meetUrl;
    }

    public Double getFatigueCoefficient() {
        return fatigueCoefficient;
    }

    public void setFatigueCoefficient(Double fatigueCoefficient) {
        this.fatigueCoefficient = fatigueCoefficient;
    }
}
