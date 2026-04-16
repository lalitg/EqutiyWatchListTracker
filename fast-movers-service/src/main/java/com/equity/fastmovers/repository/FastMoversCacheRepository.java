package com.equity.fastmovers.repository;

import com.equity.fastmovers.entity.FastMoversCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FastMoversCacheRepository extends JpaRepository<FastMoversCache, Long> {

    List<FastMoversCache> findByPriceDateOrderByTypeAscRankAsc(LocalDate priceDate);

    List<FastMoversCache> findByPriceDateAndTypeOrderByRankAsc(LocalDate priceDate, String type);

    Optional<FastMoversCache> findByPriceDateAndTypeAndRank(LocalDate priceDate, String type, int rank);

    /** Upsert a single cache row — insert or update on conflict (price_date, type, rank) */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO fast_movers_cache (price_date, type, rank, company_code, company_name, current_price, pct_change, fetched_at)
            VALUES (:priceDate, :type, :rank, :companyCode, :companyName, :currentPrice, :pctChange, :fetchedAt)
            ON CONFLICT (price_date, type, rank) DO UPDATE SET
                company_code  = EXCLUDED.company_code,
                company_name  = EXCLUDED.company_name,
                current_price = EXCLUDED.current_price,
                pct_change    = EXCLUDED.pct_change,
                fetched_at    = EXCLUDED.fetched_at
            """, nativeQuery = true)
    void upsert(@Param("priceDate") java.time.LocalDate priceDate,
                @Param("type") String type,
                @Param("rank") int rank,
                @Param("companyCode") String companyCode,
                @Param("companyName") String companyName,
                @Param("currentPrice") java.math.BigDecimal currentPrice,
                @Param("pctChange") java.math.BigDecimal pctChange,
                @Param("fetchedAt") java.time.LocalDateTime fetchedAt);
}
