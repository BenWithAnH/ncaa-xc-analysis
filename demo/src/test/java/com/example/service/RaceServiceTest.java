package com.example.service;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.example.entity.Athlete;
import com.example.repository.AthleteRepository;
import com.example.repository.RaceResultRepository;

class RaceServiceTest {

    @Mock
    private AthleteRepository athleteRepository;

    @Mock
    private RaceResultRepository raceResultRepository;

    @InjectMocks
    private RaceService raceService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetTopAthletes() {
        Athlete a1 = new Athlete();
        a1.setName("Runner 1");
        a1.setRating(150.0);
        a1.setBestTime("15:00.0");

        Athlete a2 = new Athlete();
        a2.setName("Runner 2");
        a2.setRating(140.0);
        a2.setBestTime("15:30.0");

        List<Athlete> mockAthletes = Arrays.asList(a1, a2);

        when(athleteRepository.findTop100ByBestTimeIsNotNullOrderByRatingDesc()).thenReturn(mockAthletes);

        List<Athlete> result = raceService.getTopAthletes();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("Runner 1", result.get(0).getName());
        verify(athleteRepository, times(1)).findTop100ByBestTimeIsNotNullOrderByRatingDesc();
    }

    @Test
    void testGetAthleteProfileCached() {
        String athleteLink = "http://example.com/cached_athlete";
        Athlete cachedAthlete = new Athlete();
        cachedAthlete.setName("Cached Runner");
        cachedAthlete.setRating(160.5);

        when(athleteRepository.findById(athleteLink)).thenReturn(Optional.of(cachedAthlete));

        var result = raceService.getAthleteProfile(athleteLink);

        assertNotNull(result);
        assertEquals("Cached", result.getGender());
        assertEquals(160.5, result.getPriorRating());
        verify(athleteRepository, times(1)).findById(athleteLink);
    }
}
