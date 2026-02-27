
package com.watchlist.marketdata.repository;

import com.watchlist.marketdata.entity.MarketDailySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;

public interface MarketSnapshotRepository extends JpaRepository<MarketDailySnapshot,Long>{

    boolean existsByCompanyIdAndSnapshotDate(Long id, LocalDate date);
}
