package com.companycode.nse.repository;

import com.companycode.nse.entity.CompanyMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository for the {@code company_master} table.
 *
 * <p>Provides standard CRUD operations inherited from {@link JpaRepository}
 * plus a lookup by NSE stock symbol used during incremental sync.
 */
public interface CompanyMasterRepository extends JpaRepository<CompanyMaster, Long> {

    /**
     * Finds a single {@link CompanyMaster} row by NSE stock symbol.
     *
     * <p>Used during sync to check whether a company already exists before
     * deciding to insert or skip.
     *
     * @param symbol the NSE stock symbol (e.g. {@code "INFY"})
     * @return an {@link Optional} containing the entity if found, or empty if not present
     */
    Optional<CompanyMaster> findBySymbol(String symbol);
}
