package com.equity.fundamentals.repository;

import com.equity.fundamentals.entity.CompanyPeSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Repository for the company_pe_snapshot table.
 *
 * findBySymbolAndPeDate — used by the upsert pattern in PeSnapshotService:
 *   If a snapshot already exists for (symbol, date), update it instead of inserting.
 *   Prevents duplicate rows if the job is re-run on the same day.
 *
 * findFirstBySymbolOrderByPeDateDesc — used by the API layer to get the latest
 *   P/E for a given company (most recent trading day's snapshot).
 */
@Repository
public interface CompanyPeSnapshotRepository extends JpaRepository<CompanyPeSnapshot, Long> {

    Optional<CompanyPeSnapshot> findBySymbolAndPeDate(String symbol, LocalDate peDate);

    Optional<CompanyPeSnapshot> findFirstBySymbolOrderByPeDateDesc(String symbol);
}
