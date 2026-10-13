package com.equity.watchlist.controller;

import com.equity.watchlist.dto.NotificationView;
import com.equity.watchlist.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * The bell and the panel.
 *
 * <p>Base path {@code /api/v1/notifications}, all authenticated. No endpoint accepts a user id —
 * it always comes from the JWT principal, exactly as in {@link WatchlistController} — so a caller
 * can only ever see and change their own notifications.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * GET /api/v1/notifications?limit=20
     *
     * @return the panel contents, newest first, dismissed ones excluded
     */
    @GetMapping
    public ResponseEntity<List<NotificationView>> list(Authentication authentication,
                                                       @RequestParam(required = false) Integer limit) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(notificationService.list(userId, limit));
    }

    /**
     * GET /api/v1/notifications/unread-count
     *
     * <p>The one endpoint the browser polls, so it stays a single indexed count and returns nothing
     * else.
     */
    @GetMapping("/unread-count")
    public ResponseEntity<Map<String, Long>> unreadCount(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(Map.of("count", notificationService.unreadCount(userId)));
    }

    /**
     * POST /api/v1/notifications/{id}/read
     *
     * <p>Idempotent: re-reading something already read returns 200 with {@code changed=false},
     * because the end state is what the caller wanted.
     */
    @PostMapping("/{id}/read")
    public ResponseEntity<Map<String, Object>> markRead(Authentication authentication,
                                                        @PathVariable Long id) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(Map.of("changed", notificationService.markRead(userId, id)));
    }

    /** POST /api/v1/notifications/read-all — clears the badge in one call. */
    @PostMapping("/read-all")
    public ResponseEntity<Map<String, Object>> markAllRead(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(Map.of("marked", notificationService.markAllRead(userId)));
    }

    /**
     * DELETE /api/v1/notifications/{id}
     *
     * <p>The × on a panel row. Also idempotent, and a row belonging to someone else simply reports
     * {@code changed=false} rather than revealing that it exists.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> dismiss(Authentication authentication,
                                                       @PathVariable Long id) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(Map.of("changed", notificationService.dismiss(userId, id)));
    }
}
