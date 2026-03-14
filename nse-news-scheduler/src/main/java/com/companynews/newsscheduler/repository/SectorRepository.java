package com.companynews.newsscheduler.repository;

import com.companynews.newsscheduler.model.Sector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SectorRepository extends JpaRepository<Sector, Long> {

    // Fetch only the sector name strings
    // This runs: SELECT sector_name FROM sectors
    @Query("SELECT s.sectorName FROM Sector s")
    List<String> findAllSectorNames();
}
