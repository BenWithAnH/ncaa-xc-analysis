package com.example.service;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.example.dto.IngestionReport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.example.entity.Athlete;
import com.example.entity.RaceResult;
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
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get("reconciliation_reports.csv"));
        java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get("reconciliation_reports.log"));
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

    @Test
    void testRecordReportWritesToMemoryAndFile() throws Exception {
        IngestionReport report = new IngestionReport(
                "Test XC Invite", "https://tfrrs.org/results/xc/111", "Nov 1, 2023",
                100, 95, 0, 3, 2, 0, true, List.of()
        );
        raceService.recordReport(report);

        List<IngestionReport> recent = raceService.getRecentIngestionReports();
        assertFalse(recent.isEmpty());
        assertEquals("Test XC Invite", recent.get(recent.size() - 1).meetName());
        assertEquals("SUCCESS", recent.get(recent.size() - 1).status());

        assertTrue(java.nio.file.Files.exists(java.nio.file.Paths.get("reconciliation_reports.csv")));
        assertTrue(java.nio.file.Files.exists(java.nio.file.Paths.get("reconciliation_reports.log")));

        String csvContent = java.nio.file.Files.readString(java.nio.file.Paths.get("reconciliation_reports.csv"));
        assertTrue(csvContent.contains("status,rows_touched,athletes_extracted,athletes_saved"));
        assertTrue(csvContent.contains("\"SUCCESS\""));
        assertTrue(csvContent.contains("\"Test XC Invite\""));
    }

    @Test
    void testRecordErrorReport() throws Exception {
        IngestionReport errorReport = new IngestionReport(
                "Broken Meet", "https://tfrrs.org/results/xc/999", "Oct 10, 2023",
                0, 0, 0, 0, 0, 0, false, List.of("HTTP 404 Not Found")
        );
        raceService.recordReport(errorReport);

        assertEquals("ERROR", errorReport.status());
        assertTrue(errorReport.toCompactLine().contains("[MEET-LOG] [ERROR]"));
        assertTrue(errorReport.toCompactLine().contains("HTTP 404 Not Found"));

        String csvContent = java.nio.file.Files.readString(java.nio.file.Paths.get("reconciliation_reports.csv"));
        assertTrue(csvContent.contains("\"ERROR\""));
        assertTrue(csvContent.contains("\"Broken Meet\""));
        assertTrue(csvContent.contains("HTTP 404 Not Found"));
    }

    @Test
    void testSearchAthletes() {
        Athlete a = new Athlete();
        a.setName("Nico Young");
        when(athleteRepository.findTop4ByNameContainingIgnoreCase("Nico")).thenReturn(List.of(a));

        List<Athlete> result = raceService.searchAthletes("Nico");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Nico Young", result.get(0).getName());
        verify(athleteRepository, times(1)).findTop4ByNameContainingIgnoreCase("Nico");

        // Edge case: null or blank query returns empty list
        assertTrue(raceService.searchAthletes(null).isEmpty());
        assertTrue(raceService.searchAthletes("   ").isEmpty());
    }

    @Test
    void testGetAthleteResults() {
        String link = "https://tfrrs.org/athletes/123";
        RaceResult rr = new RaceResult(link, "Nico Young", "NCAA Championship", "Nov 18, 2023", "28:50.0", 175.0);
        when(raceResultRepository.findByAthleteLink(link)).thenReturn(List.of(rr));

        List<RaceResult> result = raceService.getAthleteResults(link);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("NCAA Championship", result.get(0).getMeetName());
        verify(raceResultRepository, times(1)).findByAthleteLink(link);

        // Edge case: null or blank link returns empty list
        assertTrue(raceService.getAthleteResults(null).isEmpty());
        assertTrue(raceService.getAthleteResults("   ").isEmpty());
    }
}
