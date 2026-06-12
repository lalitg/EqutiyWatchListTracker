package com.equity.fundamentals.repository;

import com.equity.fundamentals.entity.GlobalWatchlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Read-only repository for the global_watchlist table.
 *
 * fundamentals-service uses this to get the list of companies to fetch data for.
 * global-watchlist-service owns and writes to this table.
 *
 * findAllCompanyCodes():
 *   Returns every distinct NSE symbol currently in global_watchlist.
 *   This is the source list for all scheduled and on-demand fetch jobs.
 *   The scheduler iterates only over these symbols — not all 2,200 in company_master.
 *
 * existsByCompanyCode():
 *   Used by the new-company detection job to check if a specific symbol is in the watchlist.
 */
@Repository
public interface GlobalWatchlistRepository extends JpaRepository<GlobalWatchlist, Long> {

    /**
     * Returns all distinct NSE symbols currently tracked in global_watchlist.
     * Auto-generated SQL: SELECT DISTINCT company_code FROM global_watchlist
     */
    @Query("SELECT DISTINCT g.companyCode FROM GlobalWatchlist g")
    List<String> findAllCompanyCodes();

    /**
     * Checks whether a symbol is already in global_watchlist.
     */
    boolean existsByCompanyCode(String companyCode);
}
