package com.example.dto;

import java.util.List;


public class AthleteProfileResponse {

    private String gender;
    private List<String> prs;
    private double priorRating;

    public AthleteProfileResponse() {
    }

    public AthleteProfileResponse(String gender, List<String> prs, double priorRating) {
        this.gender = gender;
        this.prs = prs;
        this.priorRating = priorRating;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public List<String> getPrs() {
        return prs;
    }

    public void setPrs(List<String> prs) {
        this.prs = prs;
    }

    public double getPriorRating() {
        return priorRating;
    }

    public void setPriorRating(double priorRating) {
        this.priorRating = priorRating;
    }
}
