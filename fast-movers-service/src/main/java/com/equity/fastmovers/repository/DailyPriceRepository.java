package com.equity.fastmovers.repository;

import com.equity.fastmovers.entity.DailyPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyPriceRepository extends JpaRepository<DailyPrice, Long> {

    Optional<DailyPrice> findByCompanyCodeAndPriceDate(String companyCode, LocalDate priceDate);

    /** Returns top N gainers comparing rank-1 (latest close) vs rank-:compareRank close */
    @Query(value = """
            WITH ranked AS (
                SELECT company_code, close_price,
                       ROW_NUMBER() OVER (PARTITION BY company_code ORDER BY price_date DESC) AS rn
                FROM daily_price
            ),
            today   AS (SELECT company_code, close_price FROM ranked WHERE rn = 1),
            compare AS (SELECT company_code, close_price FROM ranked WHERE rn = :compareRank)
            SELECT t.company_code AS companyCode,
                   t.close_price  AS currentPrice,
                   c.close_price  AS basePrice,
                   (t.close_price - c.close_price) / c.close_price * 100 AS pctChange
            FROM today t
            JOIN compare c ON t.company_code = c.company_code
            ORDER BY pctChange DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findTopGainers(@Param("compareRank") int compareRank, @Param("limit") int limit);

    @Query(value = """
            WITH ranked AS (
                SELECT company_code, close_price,
                       ROW_NUMBER() OVER (PARTITION BY company_code ORDER BY price_date DESC) AS rn
                FROM daily_price
            ),
            today   AS (SELECT company_code, close_price FROM ranked WHERE rn = 1),
            compare AS (SELECT company_code, close_price FROM ranked WHERE rn = :compareRank)
            SELECT t.company_code AS companyCode,
                   t.close_price  AS currentPrice,
                   c.close_price  AS basePrice,
                   (t.close_price - c.close_price) / c.close_price * 100 AS pctChange
            FROM today t
            JOIN compare c ON t.company_code = c.company_code
            ORDER BY pctChange ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findTopLosers(@Param("compareRank") int compareRank, @Param("limit") int limit);
}
