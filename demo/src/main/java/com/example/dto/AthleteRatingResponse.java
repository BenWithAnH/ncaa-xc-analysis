package com.example.dto;


public class AthleteRatingResponse {

    private String name;
    private String time;
    private String link;
    private double rating;
    private double priorRating;

    public AthleteRatingResponse() {
    }

    public AthleteRatingResponse(String name, String time, String link, double rating) {
        this(name, time, link, rating, 0.0);
    }

    public AthleteRatingResponse(String name, String time, String link, double rating, double priorRating) {
        this.name = name;
        this.time = time;
        this.link = link;
        double truncated = Math.floor(rating * 100) / 100;
        this.rating = truncated;
        this.priorRating = Math.floor(priorRating * 100) / 100;
    }

    public double getPriorRating() {
        return priorRating;
    }

    public void setPriorRating(double priorRating) {
        this.priorRating = priorRating;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public double getRating() {
        return rating;
    }

    public void setRating(double rating) {
        this.rating = rating;
    }
}
