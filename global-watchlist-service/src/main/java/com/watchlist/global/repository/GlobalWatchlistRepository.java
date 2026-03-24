package com.watchlist.global.repository;

import com.watchlist.global.entity.GlobalWatchlist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository for the {@code global_watchlist} table.
 *
 * <p>Provides standard CRUD operations inherited from {@link JpaRepository}
 * plus custom lookup methods by NSE company code.
 */
public interface GlobalWatchlistRepository extends JpaRepository<GlobalWatchlist, Long> {

    /**
     * Finds a single {@link GlobalWatchlist} row by NSE symbol.
     *
     * @param companyCode the NSE stock symbol (e.g. {@code "INFY"})
     * @return an {@link Optional} containing the entity if found, or empty if not tracked
     */
    Optional<GlobalWatchlist> findByCompanyCode(String companyCode);

    /**
     * Checks whether a row with the given NSE symbol already exists.
     *
     * @param companyCode the NSE stock symbol to check
     * @return {@code true} if a row exists, {@code false} otherwise
     */
    boolean existsByCompanyCode(String companyCode);
}
