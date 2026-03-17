package com.companynews.newsscheduler.repository;

import com.companynews.newsscheduler.model.Sector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SectorRepository extends JpaRepository<Sector, Long> {

    // No change needed here — JPQL uses the Java class name "Sector"
    // and field name "sectorName" — both unchanged
    // The @Table annotation on the entity handles the actual table name mapping
    @Query("SELECT s.sectorName FROM Sector s")
    List<String> findAllSectorNames();
}
