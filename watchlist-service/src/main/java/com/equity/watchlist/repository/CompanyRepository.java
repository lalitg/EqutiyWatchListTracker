package com.equity.watchlist.repository;

import com.equity.watchlist.entity.CompanyMaster;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for company_master table.
 */
public interface CompanyRepository extends JpaRepository<CompanyMaster, Long> {

    /** Returns all active companies ordered by symbol for autocomplete. */
    List<CompanyMaster> findByActiveTrueOrderBySymbolAsc();

    /** Looks up a company by its NSE symbol. */
    Optional<CompanyMaster> findBySymbol(String symbol);
}
