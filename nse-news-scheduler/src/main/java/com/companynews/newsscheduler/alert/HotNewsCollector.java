package com.companynews.newsscheduler.alert;

import com.companynews.newsscheduler.dto.NewsItem;
import com.companynews.newsscheduler.model.CompanyNews;
import com.companynews.newsscheduler.repository.CompanyNewsRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds articles that deserve an alert and have not had one.
 *
 * <h2>Why this scans rather than listens</h2>
 * Hooking into the moment news is saved would be cheaper, but an alert that exists only as an
 * in-flight event is lost whenever the process restarts between the save and the send — and this
 * service restarts often enough that the loss would be routine. Instead the state lives in the data:
 * an article carries {@code alertedAt} once it has been mailed, and a scan asks the database which
 * articles still lack it. Restarts, deployments and a failed mail server all become harmless.
 *
 * <h2>The scan is deliberately narrow</h2>
 * The database does the filtering, so a cycle with nothing to do costs one query that returns no
 * rows (measured at ~100ms over 1,900 companies) rather than loading every company's news into
 * memory. Only companies that genuinely have an unalerted, recent, threshold-crossing article are
 * read back as entities.
 *
 * <h2>Three conditions, each load-bearing</h2>
 * <ol>
 *   <li><b>|score| ≥ threshold</b> — the article is extreme enough to be worth an inbox.</li>
 *   <li><b>published within the age limit</b> — the guard that makes a re-score safe. Without it, a
 *       backfill scoring thousands of old headlines would mail everybody about months-old news.</li>
 *   <li><b>no {@code alertedAt}</b> — nobody is told the same thing twice.</li>
 * </ol>
 */
@Service
public class HotNewsCollector {

    private static final Logger log = LogManager.getLogger(HotNewsCollector.class);

    /**
     * Companies holding at least one article that qualifies.
     *
     * <p>{@code jsonb_array_elements} expands each company's news array; EXISTS stops at the first
     * match, so a company with one qualifying article costs no more than the first hit.
     */
    private static final String CANDIDATES_SQL = """
        SELECT cn.keyword
        FROM company_news cn
        WHERE EXISTS (
            SELECT 1
            FROM jsonb_array_elements(cn.news) i
            WHERE (i->>'sentimentScore') IS NOT NULL
              AND abs((i->>'sentimentScore')::numeric) >= ?
              AND (i->>'publishedAt')::bigint > ?
              AND i->>'alertedAt' IS NULL
        )
        ORDER BY cn.keyword
        LIMIT ?
        """;

    private final JdbcTemplate jdbcTemplate;
    private final CompanyNewsRepository repository;
    private final AlertProperties properties;

    public HotNewsCollector(JdbcTemplate jdbcTemplate,
                            CompanyNewsRepository repository,
                            AlertProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.repository   = repository;
        this.properties   = properties;
    }

    /**
     * @param cutoffMillis the oldest publication instant still eligible
     * @return company symbols with at least one unalerted, recent, threshold-crossing article
     */
    public List<String> candidateKeywords(long cutoffMillis) {
        try {
            return jdbcTemplate.queryForList(CANDIDATES_SQL, String.class,
                                             properties.getThreshold(),
                                             cutoffMillis,
                                             properties.getMaxCompaniesPerCycle());
        } catch (Exception e) {
            log.error("Alert candidate scan failed — no alerts this cycle: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * The qualifying articles for one company, newest first.
     *
     * <p>Re-checks in Java what the SQL already filtered on. That is not redundant: the row may have
     * changed between the scan and this read, and the email must describe what is true now.
     *
     * @param keyword      company symbol
     * @param cutoffMillis the oldest publication instant still eligible
     */
    public List<HotArticle> hotArticlesFor(String keyword, long cutoffMillis) {
        CompanyNews record = repository.findByKeyword(keyword).orElse(null);
        if (record == null || record.getNews() == null) return List.of();

        double threshold = properties.getThreshold();
        List<HotArticle> hot = new ArrayList<>();

        for (NewsItem item : record.getNews()) {
            Double score = item.getSentimentScore();
            Long published = item.getPublishedAt();

            if (score == null || published == null) continue;
            if (Math.abs(score) < threshold) continue;
            if (published <= cutoffMillis) continue;
            if (item.getAlertedAt() != null) continue;
            if (item.getLink() == null || item.getLink().isBlank()) continue;

            hot.add(new HotArticle(keyword, item.getLink(), item.getSummary(), score, published));
        }

        hot.sort((a, b) -> Long.compare(b.publishedAt(), a.publishedAt()));
        return hot;
    }
}
