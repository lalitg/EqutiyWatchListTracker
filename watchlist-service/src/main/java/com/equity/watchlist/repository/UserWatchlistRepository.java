package com.equity.watchlist.repository;

import com.equity.watchlist.entity.UserWatchlist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link UserWatchlist} operations.
 */
public interface UserWatchlistRepository extends JpaRepository<UserWatchlist, Long> {

    /** Returns all named watchlists for a user, ordered by creation time. */
    List<UserWatchlist> findByUserIdOrderByCreatedAtAsc(Long userId);

    /** Finds a specific watchlist by user ID and watchlist ID (ownership check). */
    Optional<UserWatchlist> findByUserIdAndId(Long userId, Long id);

    /** Checks if a user already has a watchlist with the given name. */
    boolean existsByUserIdAndName(Long userId, String name);

    /** Returns the first watchlist for a user (used as default until auth is implemented). */
    Optional<UserWatchlist> findFirstByUserIdOrderByCreatedAtAsc(Long userId);
}
