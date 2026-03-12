package com.companycode.nse.repository;

import com.companycode.nse.entity.SectorCompanies;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for SectorCompanies entity.
 * Provides standard CRUD operations via JpaRepository
 * plus a lookup by sector name used during upsert.
 */
public interface SectorCompaniesRepository extends JpaRepository<SectorCompanies, Long> {

    // Used during sync to check if a sector row already exists.
    // If present → update it. If empty → create a new row.
    Optional<SectorCompanies> findBySectorName(String sectorName);
}
