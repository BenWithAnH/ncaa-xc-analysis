package com.example.repository;

import com.example.entity.Athlete;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AthleteRepository extends JpaRepository<Athlete, String> {
    List<Athlete> findTop100ByBestTimeIsNotNullOrderByRatingDesc();
}
