package com.example.dto;

public record BulkScrapeStatusResponse(
    boolean running,
    String stage,
    int startYear,
    int totalPages,
    int totalMeets,
    int processedMeets,
    int remainingMeets,
    double avgTimePerMeetMs,
    long etaSeconds,
    long elapsedSeconds,
    String currentMeetName,
    int savedAthletes,
    String message,
    String error
) {}
