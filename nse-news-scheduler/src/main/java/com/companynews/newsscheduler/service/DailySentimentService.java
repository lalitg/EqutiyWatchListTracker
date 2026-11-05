package com.companynews.newsscheduler.service;

import com.companynews.newsscheduler.dto.NewsItem;
import com.companynews.newsscheduler.model.CompanyDailySentiment;
import com.companynews.newsscheduler.model.CompanyNews;
import com.companynews.newsscheduler.repository.CompanyDailySentimentRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maintains the per-company, per-day sentiment rollup that the Extremes page ranks over.
 *
 * <h2>What keeps "Today" live</h2>
 * {@link NewsWorker} calls {@link #rebuildFor(CompanyNews)} on every save, so a company's rows are
 * rewritten the moment its article list changes — which is what makes the Today board move on the
 * fifteen-minute fetch cycle without any scheduled job driving it.
 *
 * <h2>Why a full rebuild rather than an increment</h2>
 * Adding each new article's score to today's row would be cheaper, but it drifts: deduplication can
 * reject an article after it has been counted, cleanup deletes old ones, and a re-run of the
 * backfill would double-count. Recomputing a company's buckets from its stored articles is
 * idempotent — running it twice leaves the same answer as running it once — and the work is a
 * single pass over one already-loaded array.
 *
 * <h2>Absent rather than zero</h2>
 * A company with no news on a day gets no row for that day. That is what keeps it out of the
 * ranking rather than placing it at the neutral midpoint, where it would outrank genuinely negative
 * companies and be outranked by genuinely positive ones — for no reason but silence.
 */
@Service
public class DailySentimentService {

    private static final Logger log = LogManager.getLogger(DailySentimentService.class);

    /** Days are Indian market days, matching the schedulers, cleanup, and the Sentiments tab. */
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final CompanyDailySentimentRepository repository;

    public DailySentimentService(CompanyDailySentimentRepository repository) {
        this.repository = repository;
    }

    /**
     * Recomputes every daily bucket for one company from its stored articles.
     *
     * <p>Rows for days the company no longer has articles in are removed, so a cleanup that deletes
     * the last article of a day does not leave a stale bucket ranking forever.
     *
     * @param record the company's news record; ignored when {@code null} or empty of scored news
     * @return how many daily rows the company now has
     */
    @Transactional
    public int rebuildFor(CompanyNews record) {
        if (record == null || record.getKeyword() == null) return 0;

        Map<LocalDate, double[]> buckets = bucketByDay(record);   // [sum, count]

        List<CompanyDailySentiment> existing = repository.findByKeyword(record.getKeyword());
        Map<LocalDate, CompanyDailySentiment> byDay = new HashMap<>();
        for (CompanyDailySentiment row : existing) byDay.put(row.getDay(), row);

        List<CompanyDailySentiment> toSave = new ArrayList<>();
        List<CompanyDailySentiment> toDelete = new ArrayList<>();

        for (Map.Entry<LocalDate, double[]> entry : buckets.entrySet()) {
            double sum = entry.getValue()[0];
            int count  = (int) entry.getValue()[1];

            CompanyDailySentiment row = byDay.remove(entry.getKey());
            if (row == null) {
                toSave.add(new CompanyDailySentiment(record.getKeyword(), entry.getKey(), sum, count));
            } else if (!equalsRounded(row.getScoreSum(), sum) || !Integer.valueOf(count).equals(row.getArticleCount())) {
                // Only write when a value actually moved. This runs on every save for every active
                // company, so rewriting unchanged rows would multiply writes for nothing.
                row.setScoreSum(sum);
                row.setArticleCount(count);
                toSave.add(row);
            }
        }

        // Anything left in byDay is a day the company no longer has articles for.
        toDelete.addAll(byDay.values());

        if (!toSave.isEmpty())   repository.saveAll(toSave);
        if (!toDelete.isEmpty()) repository.deleteAll(toDelete);

        if (!toSave.isEmpty() || !toDelete.isEmpty()) {
            log.debug("Daily rollup keyword={} days={} written={} removed={}",
                      record.getKeyword(), buckets.size(), toSave.size(), toDelete.size());
        }
        return buckets.size();
    }

    /**
     * Deletes rollup rows older than the retention window.
     *
     * @param cutoff the earliest day to keep
     * @return how many rows were removed
     */
    @Transactional
    public int pruneOlderThan(LocalDate cutoff) {
        int removed = repository.deleteOlderThan(cutoff);
        if (removed > 0) log.info("Daily rollup pruned {} row(s) older than {}", removed, cutoff);
        return removed;
    }

    /** @return today in Indian market time — the day the Extremes page labels "Today" */
    public static LocalDate today() {
        return LocalDate.now(IST);
    }

    // ── internals ──────────────────────────────────────────────────────────────

    /**
     * Groups a company's scored articles into calendar-day sums and counts.
     *
     * <p>Undated articles are excluded. One cannot be placed on a day, and assuming a day would put
     * it in a bucket it may not belong to — silently distorting a ranking nobody can audit.
     */
    private Map<LocalDate, double[]> bucketByDay(CompanyNews record) {
        Map<LocalDate, double[]> buckets = new HashMap<>();
        if (record.getNews() == null) return buckets;

        for (NewsItem item : record.getNews()) {
            Double score = item.getSentimentScore();
            if (score == null) continue;

            Long publishedAt = publishedAtOf(item);
            if (publishedAt == null) continue;

            LocalDate day = Instant.ofEpochMilli(publishedAt).atZone(IST).toLocalDate();
            double[] bucket = buckets.computeIfAbsent(day, d -> new double[2]);
            bucket[0] += score;
            bucket[1] += 1;
        }
        return buckets;
    }

    /** Publication instant, parsing the date string only for items written before the field existed. */
    private static Long publishedAtOf(NewsItem item) {
        if (item.getPublishedAt() != null) return item.getPublishedAt();
        return NewsDateParser.toEpochMillis(item.getDate());
    }

    /** Doubles accumulated in a different order can differ in the last bit; compare at storage precision. */
    private static boolean equalsRounded(Double a, double b) {
        if (a == null) return false;
        return Math.abs(a - b) < 1e-9;
    }
}
