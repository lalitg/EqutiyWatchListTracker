package com.equity.watchlist.repository;

import com.equity.watchlist.entity.UserNotification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Reads and writes the reader's own notifications.
 *
 * <p>Every method takes a user id, and the service always passes the caller's own. Nothing here can
 * be asked for another person's notifications, which is what makes the controller safe even though
 * the panel addresses rows by a bare numeric id.
 */
public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {

    /** The panel: newest first, dismissed ones excluded. */
    List<UserNotification> findByUserIdAndDismissedAtIsNullOrderByCreatedAtDesc(Long userId,
                                                                               Pageable pageable);

    /** The badge. Indexed, and the only query the browser polls. */
    long countByUserIdAndReadAtIsNullAndDismissedAtIsNull(Long userId);

    /** Scoped lookup — a row that belongs to someone else simply is not found. */
    Optional<UserNotification> findByIdAndUserId(Long id, Long userId);

    /**
     * Marks everything currently unread as read, in one statement rather than row by row.
     *
     * @return how many were marked
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           UPDATE UserNotification n
              SET n.readAt = :now
            WHERE n.userId = :userId
              AND n.readAt IS NULL
              AND n.dismissedAt IS NULL
           """)
    int markAllRead(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
