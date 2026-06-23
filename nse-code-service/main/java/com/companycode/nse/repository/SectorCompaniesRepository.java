package com.companycode.nse.repository;

import com.companycode.nse.entity.SectorCompanies;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SectorCompaniesRepository extends JpaRepository<SectorCompanies, Long> {

    List<SectorCompanies> findBySymbol(String symbol);

    @Modifying
    @Query("DELETE FROM SectorCompanies")
    void deleteAllBulk();

    List<SectorCompanies> findByDisplayName(String displayName);

    /** Distinct {displayName, newsKeyword} pairs for the 10 domestic tab sectors. */
    @Query("SELECT DISTINCT s.displayName, s.newsKeyword FROM SectorCompanies s " +
           "WHERE s.newsKeyword IS NOT NULL ORDER BY s.displayName")
    List<Object[]> findDistinctDomesticSectors();
}
