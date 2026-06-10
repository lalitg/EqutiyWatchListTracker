package com.equity.watchlist.repository;

import com.equity.watchlist.entity.Watchlist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Watchlist} operations.
 */
public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {

    /** Finds all entries for a named watchlist, ordered by creation time (oldest first). */
    List<Watchlist> findByUserWatchlistIdOrderByCreatedAtAsc(Long userWatchlistId);

    /** Finds a specific entry by watchlist ID and company code. */
    Optional<Watchlist> findByUserWatchlistIdAndCompanyCode(Long userWatchlistId, String companyCode);

    /** Deletes a specific entry by watchlist ID and company code. */
    void deleteByUserWatchlistIdAndCompanyCode(Long userWatchlistId, String companyCode);

    /** Deletes all entries for a named watchlist in a single query. */
    void deleteByUserWatchlistId(Long userWatchlistId);

    /** Counts entries for a named watchlist. */
    long countByUserWatchlistId(Long userWatchlistId);
}
