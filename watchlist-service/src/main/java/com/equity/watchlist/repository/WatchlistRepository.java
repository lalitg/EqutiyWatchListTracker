package com.equity.watchlist.repository;

import com.equity.watchlist.entity.Watchlist;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for watchlist operations.
 * Provides CRUD operations and custom queries.
 */
public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {

    /** Finds all entries for a user, ordered by creation time (oldest first). */
    List<Watchlist> findByUserIdOrderByCreatedAtAsc(Long userId);

    /** Finds a specific entry by user ID and company code. */
    Optional<Watchlist> findByUserIdAndCompanyCode(Long userId, String companyCode);

    /** Deletes a specific entry by user ID and company code. */
    void deleteByUserIdAndCompanyCode(Long userId, String companyCode);

    /** Counts entries for a user. */
    long countByUserId(Long userId);
}
