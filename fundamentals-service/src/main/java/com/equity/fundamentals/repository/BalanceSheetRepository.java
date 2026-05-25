package com.equity.fundamentals.repository;

import com.equity.fundamentals.entity.BalanceSheet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for the balance_sheets table.
 *
 * findBySymbolAndFiscalYear — used by the upsert pattern:
 *   The service calls this before saving. If a row exists for (symbol, fiscalYear),
 *   it updates it. If not, it creates a new one.
 *
 * findTop3BySymbolOrderByPeriodEndDateDesc — used by main-api-service
 *   to fetch the last 3 annual balance sheets for display.
 *
 * findDistinctSymbols — used by the new-company detection job in FundamentalsScheduler:
 *   Returns all symbols that already have balance sheet data in the DB.
 *   Compared against global_watchlist to find newly added companies.
 */
@Repository
public interface BalanceSheetRepository extends JpaRepository<BalanceSheet, Long> {

    Optional<BalanceSheet> findBySymbolAndFiscalYear(String symbol, String fiscalYear);

    /**
     * Returns all symbols that already have at least one balance sheet row.
     * SQL: SELECT DISTINCT symbol FROM balance_sheets
     */
    @Query("SELECT DISTINCT b.symbol FROM BalanceSheet b")
    List<String> findDistinctSymbols();

    List<BalanceSheet> findTop3BySymbolOrderByPeriodEndDateDesc(String symbol);

    List<BalanceSheet> findBySymbolOrderByPeriodEndDateDesc(String symbol);
}
