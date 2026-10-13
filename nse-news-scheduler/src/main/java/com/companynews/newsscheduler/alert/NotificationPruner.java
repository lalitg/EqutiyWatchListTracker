package com.companynews.newsscheduler.alert;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Deletes notifications older than the retention window, once a night.
 *
 * <h2>Why seven days</h2>
 * A sentiment reading on a week-old headline is not news any more, and nobody scrolls that far
 * down a notification panel. Keeping the window short is also what stops the table growing without
 * bound as users arrive: at any moment it holds roughly a week of alerts, whatever the history.
 *
 * <h2>Why it runs here and not in the web service</h2>
 * This JVM already owns the scheduled jobs and the alert thread. main-api runs no scheduler at all,
 * and giving it one for a nightly delete would be a new moving part for nothing.
 *
 * <p>It runs on the alert scheduler rather than the shared one, for the same reason sending did:
 * the shared thread also runs news fetching and the hourly cleanup.
 */
@Service
public class NotificationPruner {

    private static final Logger log = LogManager.getLogger(NotificationPruner.class);

    private static final String DELETE_SQL =
        "DELETE FROM user_notifications WHERE created_at < now() - make_interval(days => ?)";

    private final JdbcTemplate jdbcTemplate;
    private final int retentionDays;
    private final boolean enabled;

    public NotificationPruner(JdbcTemplate jdbcTemplate,
                              @Value("${notifications.retention-days:7}") int retentionDays,
                              @Value("${notifications.enabled:true}") boolean enabled) {
        this.jdbcTemplate  = jdbcTemplate;
        this.retentionDays = retentionDays;
        this.enabled       = enabled;
    }

    @Scheduled(cron = "${notifications.prune.cron:0 0 3 * * *}",
               scheduler = "alertTaskScheduler",
               zone = "${scheduler.timezone:Asia/Kolkata}")
    public void prune() {
        if (!enabled) return;
        pruneNow();
    }

    /** Exposed so the retention rule can be exercised without waiting for 03:00. */
    public int pruneNow() {
        try {
            int removed = jdbcTemplate.update(DELETE_SQL, retentionDays);
            if (removed > 0) {
                log.info("Pruned {} notification(s) older than {} days", removed, retentionDays);
            }
            return removed;
        } catch (Exception e) {
            // The table belongs to another service and may not exist yet mid-deployment. A failed
            // prune is a tidiness problem, never a reason to disturb anything else.
            log.error("Notification prune failed: {}", e.getMessage());
            return 0;
        }
    }
}
