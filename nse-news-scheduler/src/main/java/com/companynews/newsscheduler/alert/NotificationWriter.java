package com.companynews.newsscheduler.alert;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;

/**
 * Raises in-app notifications — the delivery channel that replaced email.
 *
 * <h2>Why plain SQL again</h2>
 * {@code user_notifications} is owned by watchlist-service, which runs in the other JVM and maps it
 * with JPA. Mapping the same table a second time here is how two services quietly disagree about a
 * schema. This writes with {@link JdbcTemplate}, exactly as {@link SubscriberLookup} reads
 * subscriptions and {@code CompanyNameLookup} reads company names.
 *
 * <h2>Why ON CONFLICT DO NOTHING</h2>
 * The article stamp is written after the notifications, so a crash in between would make the next
 * cycle try the same article again. The unique index on (user, article) turns that retry into a
 * no-op rather than a duplicate in somebody's panel. Belt and braces: the stamp is the primary
 * guard, this is what makes the window between the two harmless.
 */
@Service
public class NotificationWriter {

    private static final Logger log = LogManager.getLogger(NotificationWriter.class);

    private static final String INSERT_SQL = """
        INSERT INTO user_notifications
            (user_id, company_code, headline, article_url, score, published_at, created_at)
        VALUES (?, ?, ?, ?, ?, ?, now())
        ON CONFLICT (user_id, article_url) DO NOTHING
        """;

    private final JdbcTemplate jdbcTemplate;

    public NotificationWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Writes one notification per subscriber for one article.
     *
     * @param article     the article that crossed the threshold
     * @param subscribers everyone who follows that company
     * @return how many rows were actually created; zero when every recipient already had it
     */
    public int raise(HotArticle article, List<SubscriberLookup.Subscriber> subscribers) {
        if (subscribers == null || subscribers.isEmpty()) return 0;

        int created = 0;
        for (SubscriberLookup.Subscriber subscriber : subscribers) {
            try {
                created += jdbcTemplate.update(INSERT_SQL,
                        subscriber.userId(),
                        article.keyword(),
                        headline(article),
                        article.link(),
                        article.score(),
                        new Timestamp(article.publishedAt()));
            } catch (Exception e) {
                // One recipient failing must not cost the others theirs, and must not fail the
                // cycle. The article stays unstamped, so the next cycle retries this user.
                log.error("Could not raise a notification for userId={} on {}: {}",
                          subscriber.userId(), article.keyword(), e.getMessage());
            }
        }
        return created;
    }

    /**
     * A headline is required by the table, and an article without one is still worth telling
     * someone about — the company and the link carry the meaning.
     */
    private static String headline(HotArticle article) {
        String headline = article.headline();
        return headline == null || headline.isBlank()
             ? article.keyword() + " is in the news"
             : headline;
    }
}
