package com.example.dto;

/**
 * Represents an athlete's rating for a specific meet appearance in the dataset.
 */
public record MeetRatingInfo(
    String athleteName,
    String athleteLink,
    String meetName,
    String raceDate,
    String raceTime,
    Double rating
) {}
