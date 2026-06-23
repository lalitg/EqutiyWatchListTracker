package com.companycode.nse.repository;

import com.companycode.nse.entity.SectorCompanies;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository for the {@code sector_companies} table.
 *
 * <p>Provides standard CRUD operations inherited from {@link JpaRepository}
 * plus a lookup by sector name used during upsert in
 * {@link com.companycode.nse.service.NseSectorSyncService}.
 */
public interface SectorCompaniesRepository extends JpaRepository<SectorCompanies, Long> {

    /**
     * Finds a single {@link SectorCompanies} row by sector name.
     *
     * <p>Used during sync to determine whether to update an existing row
     * or insert a new one.
     *
     * @param sectorName the NSE sectoral index name (e.g. {@code "NIFTY IT"})
     * @return an {@link Optional} containing the entity if found, or empty if not present
     */
    Optional<SectorCompanies> findBySectorName(String sectorName);
}
