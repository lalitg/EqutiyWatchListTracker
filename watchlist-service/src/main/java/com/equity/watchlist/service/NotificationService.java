package com.equity.watchlist.service;

import com.equity.watchlist.dto.NotificationView;
import com.equity.watchlist.entity.UserNotification;
import com.equity.watchlist.repository.UserNotificationRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * The reader's own notifications: what to show, what has been seen, what has been dismissed.
 *
 * <h2>Scoping is the security model</h2>
 * Every method takes the caller's user id from their token and every query filters on it. A row
 * belonging to someone else is not "forbidden" here, it is simply <em>not found</em> — which is
 * both safer and quieter than a 403, since it tells a prober nothing about whether an id exists.
 *
 * <h2>Reading and dismissing are different acts</h2>
 * Opening an item clears it from the badge but leaves it in the list, because people reread things.
 * Dismissing removes it from the list for good: pressing × is a decision, not a filter. Neither
 * deletes the row — the unique constraint on (user, article) still has to block the same article
 * arriving twice, and the nightly prune is what actually frees space.
 */
@Service
@Transactional
public class NotificationService {

    private static final Logger logger = LogManager.getLogger(NotificationService.class);

    /** Enough to fill the panel; more than anyone scrolls in one sitting. */
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final UserNotificationRepository repository;

    public NotificationService(UserNotificationRepository repository) {
        this.repository = repository;
    }

    /**
     * The panel contents: newest first, dismissed ones gone.
     *
     * @param limit how many to return; clamped so a crafted query cannot ask for the whole table
     */
    @Transactional(readOnly = true)
    public List<NotificationView> list(Long userId, Integer limit) {
        int size = limit == null || limit < 1 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        return repository
                .findByUserIdAndDismissedAtIsNullOrderByCreatedAtDesc(userId, PageRequest.of(0, size))
                .stream()
                .map(NotificationView::from)
                .toList();
    }

    /** The number on the bell: unread and not dismissed. */
    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return repository.countByUserIdAndReadAtIsNullAndDismissedAtIsNull(userId);
    }

    /**
     * Marks one item read.
     *
     * @return true if something changed; false when the row is not this user's, or was already read
     */
    public boolean markRead(Long userId, Long notificationId) {
        Optional<UserNotification> found = repository.findByIdAndUserId(notificationId, userId);
        if (found.isEmpty()) return false;

        UserNotification notification = found.get();
        if (notification.getReadAt() != null) return false;

        notification.setReadAt(LocalDateTime.now());
        repository.save(notification);
        return true;
    }

    /** Marks everything unread as read — the "mark all read" control on the panel. */
    public int markAllRead(Long userId) {
        int marked = repository.markAllRead(userId, LocalDateTime.now());
        if (marked > 0) logger.debug("User {} marked {} notification(s) read", userId, marked);
        return marked;
    }

    /**
     * Dismisses one item: gone from the list, gone from the badge, permanently.
     *
     * <p>Dismissing something already dismissed is success, not an error — a double click, or a
     * second tab, has still produced exactly the state the user asked for.
     *
     * @return true if this call dismissed it, false if it was not this user's or already gone
     */
    public boolean dismiss(Long userId, Long notificationId) {
        Optional<UserNotification> found = repository.findByIdAndUserId(notificationId, userId);
        if (found.isEmpty()) return false;

        UserNotification notification = found.get();
        if (notification.getDismissedAt() != null) return false;

        LocalDateTime now = LocalDateTime.now();
        notification.setDismissedAt(now);
        // Dismissed without opening still means "seen and decided", so it must leave the badge.
        if (notification.getReadAt() == null) notification.setReadAt(now);
        repository.save(notification);
        return true;
    }
}
