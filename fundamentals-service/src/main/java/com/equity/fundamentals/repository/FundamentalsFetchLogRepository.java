package com.equity.fundamentals.repository;

import com.equity.fundamentals.entity.FundamentalsFetchLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for the fundamentals_fetch_log table.
 *
 * Used by QuarterlyResultsService and BalanceSheetService to write
 * one audit row per company per run — SUCCESS or FAILED.
 *
 * findByStatusAndFetchType — useful for re-running only failed companies.
 */
@Repository
public interface FundamentalsFetchLogRepository extends JpaRepository<FundamentalsFetchLog, Long> {

    List<FundamentalsFetchLog> findByStatusAndFetchType(String status, String fetchType);
}
