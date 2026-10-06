package com.companynews.newsscheduler.alert;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds who should be emailed about which company.
 *
 * <h2>Why plain SQL</h2>
 * {@code company_subscriptions} and {@code users} are owned by the other JVM — the one serving the
 * website. This service has no entities for them and must not grow any: two JPA mappings of the
 * same table in two processes is how schemas quietly diverge. Reading with {@link JdbcTemplate} is
 * the same approach {@code CompanyNameLookup} already uses for {@code company_master}.
 *
 * <h2>Why not a cached map</h2>
 * The mentor's design keeps subscribers in a {@code ConcurrentHashMap} refreshed at startup. That
 * assumes one process. Subscribe requests land in the <em>other</em> JVM, so a cache here would
 * never see a new subscription until this service restarted — a quarter of an hour during which a
 * user who just pressed "Alert me" silently gets nothing. One indexed query per cycle is correct
 * and, at this size, costs under a millisecond.
 *
 * <h2>Only verified addresses</h2>
 * The join requires {@code email_verified}. An unverified address has never been proved to belong
 * to the person who typed it, and mailing it risks bounces that put the whole sending account at
 * risk.
 */
@Service
public class SubscriberLookup {

    private static final Logger log = LogManager.getLogger(SubscriberLookup.class);

    private static final String SQL = """
        SELECT s.company_code, u.id AS user_id, u.email, u.name
        FROM company_subscriptions s
        JOIN users u ON u.id = s.user_id
        WHERE s.company_code IN (%s)
          AND u.email IS NOT NULL
          AND u.email_verified IS TRUE
          AND u.status = 'ACTIVE'
        """;

    private final JdbcTemplate jdbcTemplate;

    public SubscriberLookup(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** One person who should hear about one or more companies. */
    public record Subscriber(Long userId, String email, String name) {}

    /**
     * @param companyCodes the companies with alertable news this cycle
     * @return company symbol → the people subscribed to it; companies with no subscribers are absent
     */
    public Map<String, List<Subscriber>> subscribersFor(Collection<String> companyCodes) {
        Map<String, List<Subscriber>> bySymbol = new HashMap<>();
        if (companyCodes == null || companyCodes.isEmpty()) return bySymbol;

        String placeholders = String.join(",", java.util.Collections.nCopies(companyCodes.size(), "?"));
        Object[] args = companyCodes.toArray();

        try {
            jdbcTemplate.query(String.format(SQL, placeholders), rs -> {
                bySymbol.computeIfAbsent(rs.getString("company_code"), k -> new java.util.ArrayList<>())
                        .add(new Subscriber(rs.getLong("user_id"),
                                            rs.getString("email"),
                                            rs.getString("name")));
            }, args);
        } catch (Exception e) {
            // The subscriptions table belongs to another service and may not exist yet on a server
            // mid-deployment. Alerts going quiet is a far better failure than news fetching dying.
            log.error("Could not read alert subscribers — no alerts this cycle: {}", e.getMessage());
            return Map.of();
        }

        return bySymbol;
    }
}
