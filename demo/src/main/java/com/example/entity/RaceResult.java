package com.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "race_results")
public class RaceResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String athleteLink;
    
    private String athleteName;

    private String meetName;

    private String raceDate;

    private String raceTime;

    private Double priorRating;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public RaceResult() {
    }

    public RaceResult(String athleteLink, String athleteName, String meetName, String raceDate, String raceTime, Double priorRating) {
        this.athleteLink = athleteLink;
        this.athleteName = athleteName;
        this.meetName = meetName;
        this.raceDate = raceDate;
        this.raceTime = raceTime;
        this.priorRating = priorRating;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAthleteLink() {
        return athleteLink;
    }

    public void setAthleteLink(String athleteLink) {
        this.athleteLink = athleteLink;
    }

    public String getAthleteName() {
        return athleteName;
    }

    public void setAthleteName(String athleteName) {
        this.athleteName = athleteName;
    }

    public String getMeetName() {
        return meetName;
    }

    public void setMeetName(String meetName) {
        this.meetName = meetName;
    }

    public String getRaceDate() {
        return raceDate;
    }

    public void setRaceDate(String raceDate) {
        this.raceDate = raceDate;
    }

    public String getRaceTime() {
        return raceTime;
    }

    public void setRaceTime(String raceTime) {
        this.raceTime = raceTime;
    }

    public Double getPriorRating() {
        return priorRating;
    }

    public void setPriorRating(Double priorRating) {
        this.priorRating = priorRating;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
