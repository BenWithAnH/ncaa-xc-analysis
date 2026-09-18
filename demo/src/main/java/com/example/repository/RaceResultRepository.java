package com.example.repository;

import com.example.entity.RaceResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RaceResultRepository extends JpaRepository<RaceResult, Long> {
    List<RaceResult> findByAthleteLink(String athleteLink);
    List<RaceResult> findByMeetName(String meetName);
    List<RaceResult> findByAthleteNameIgnoreCase(String athleteName);
    List<RaceResult> findByAthleteNameContainingIgnoreCase(String athleteName);
}
