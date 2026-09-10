package com.companynews.newsscheduler.repository;

import com.companynews.newsscheduler.model.Sector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for the {@code sector_companies} table.
 *
 * <p>Provides access to all market sector names used as search keywords in the
 * Google RSS scheduler. Sector names (e.g., {@code Banking}, {@code Pharma}) are loaded
 * alongside company symbols and macro keywords to build the full keyword list for each
 * scheduled fetch run.
 */
@Repository
public interface SectorRepository extends JpaRepository<Sector, Long> {

    /**
     * Returns all sector names stored in the {@code sector_companies} table.
     *
     * <p>Uses JPQL with the Java field name {@code sectorName} and entity class name
     * {@code Sector}. The {@code @Table(name = "sector_companies")} annotation on the entity
     * maps this to the correct physical table.
     *
     * <p>Generated SQL equivalent: {@code SELECT sector_name FROM sector_companies}
     *
     * @return a list of sector name strings (e.g., {@code ["Banking", "Pharmaceuticals"]});
     *         returns an empty list if the table is empty
     */
    @Query("SELECT DISTINCT s.sectorName FROM Sector s WHERE s.sectorName IS NOT NULL")
    List<String> findAllSectorNames();
}
