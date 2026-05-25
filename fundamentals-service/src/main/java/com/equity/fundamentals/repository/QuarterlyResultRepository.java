package com.equity.fundamentals.repository;

import com.equity.fundamentals.entity.QuarterlyResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for the quarterly_results table.
 *
 * findBySymbolAndQuarter — used by the upsert pattern:
 *   The service calls this before saving. If a row exists, it updates it.
 *   If not, it creates a new row. This avoids duplicate rows on re-runs.
 *
 * findDistinctSymbols — used by the new-company detection job in FundamentalsScheduler:
 *   Returns all symbols that already have quarterly data in the DB.
 *   Compared against global_watchlist to find newly added companies
 *   that need an immediate on-demand fetch.
 *
 * findTop4BySymbolOrderByPeriodEndDateDesc — used by main-api-service
 *   to fetch the last 4 quarters for a company for display.
 */
@Repository
public interface QuarterlyResultRepository extends JpaRepository<QuarterlyResult, Long> {

    Optional<QuarterlyResult> findBySymbolAndQuarter(String symbol, String quarter);

    /**
     * Returns all symbols that already have at least one quarterly result row.
     * Used by the new-company detection job to find symbols with no data yet.
     * SQL: SELECT DISTINCT symbol FROM quarterly_results
     */
    @Query("SELECT DISTINCT q.symbol FROM QuarterlyResult q")
    List<String> findDistinctSymbols();

    List<QuarterlyResult> findTop4BySymbolOrderByPeriodEndDateDesc(String symbol);

    List<QuarterlyResult> findBySymbolOrderByPeriodEndDateDesc(String symbol);
}
