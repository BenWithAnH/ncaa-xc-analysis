package com.example;

import com.example.controller.Controller;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.example.dto.AthleteProfileResponse;
import com.example.dto.AthleteRatingResponse;
import com.example.dto.MeetResponse;
import com.example.dto.RaceRequest;
import com.example.dto.RaceResultResponse;
import com.example.entity.Athlete;
import com.example.service.RaceService;

class ControllerTest {

    @Mock
    private RaceService raceService;

    @InjectMocks
    private Controller controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetTopAthletes() {
        Athlete athlete1 = new Athlete();
        athlete1.setName("John Doe");
        Athlete athlete2 = new Athlete();
        athlete2.setName("Jane Smith");
        
        List<Athlete> mockAthletes = Arrays.asList(athlete1, athlete2);
        
        when(raceService.getTopAthletes()).thenReturn(mockAthletes);

        List<Athlete> result = controller.getTopAthletes();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("John Doe", result.get(0).getName());
        verify(raceService, times(1)).getTopAthletes();
    }

    @Test
    void testRateRace() {
        RaceRequest request = new RaceRequest();
        request.setMeetUrl("http://example.com/meet");
        request.setMeetName("Example Meet");
        request.setMeetDate("Nov 2, 2023");

        RaceResultResponse mockResponse = new RaceResultResponse();
        mockResponse.setMeetUrl("http://example.com/meet");

        when(raceService.scrapeAndRateRace("http://example.com/meet", "Example Meet", "Nov 2, 2023")).thenReturn(mockResponse);

        RaceResultResponse result = controller.rateRace(request);

        assertNotNull(result);
        assertEquals("http://example.com/meet", result.getMeetUrl());
        verify(raceService, times(1)).scrapeAndRateRace("http://example.com/meet", "Example Meet", "Nov 2, 2023");
    }

    @Test
    void testGetRaceResults() {
        String meetUrl = "http://example.com/meet";
        AthleteRatingResponse arr = new AthleteRatingResponse();
        arr.setName("Test Runner");
        List<AthleteRatingResponse> mockResults = Arrays.asList(arr);
        
        when(raceService.scrapeRaceResults(meetUrl)).thenReturn(mockResults);
        
        List<AthleteRatingResponse> result = controller.getRaceResults(meetUrl);
        
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Test Runner", result.get(0).getName());
        verify(raceService, times(1)).scrapeRaceResults(meetUrl);
    }

    @Test
    void testListMeets() {
        MeetResponse meet = new MeetResponse();
        meet.setName("Mock Meet");
        List<MeetResponse> mockMeets = Arrays.asList(meet);
        
        when(raceService.listXCMeets(1)).thenReturn(mockMeets);
        
        List<MeetResponse> result = controller.listMeets(1);
        
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Mock Meet", result.get(0).getName());
        verify(raceService, times(1)).listXCMeets(1);
    }

    @Test
    void testBulkScrape() {
        when(raceService.bulkScrapeMeets(2)).thenReturn(150);
        
        String result = controller.bulkScrape(2);
        
        assertEquals("Bulk scrape completed. Saved 150 athletes across 2 page(s) of meets.", result);
        verify(raceService, times(1)).bulkScrapeMeets(2);
    }

    @Test
    void testGetAthleteProfile() {
        String link = "http://example.com/athlete";
        AthleteProfileResponse mockProfile = new AthleteProfileResponse();
        mockProfile.setGender("Male");
        
        when(raceService.getAthleteProfile(link)).thenReturn(mockProfile);
        
        AthleteProfileResponse result = controller.getAthleteProfile(link);
        
        assertNotNull(result);
        assertEquals("Male", result.getGender());
        verify(raceService, times(1)).getAthleteProfile(link);
    }
}
