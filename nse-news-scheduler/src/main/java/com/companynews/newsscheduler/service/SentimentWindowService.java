package com.companynews.newsscheduler.service;

import com.companynews.newsscheduler.dto.LatestSentimentDto;
import com.companynews.newsscheduler.dto.NewsItem;
import com.companynews.newsscheduler.dto.SentimentWindow;
import com.companynews.newsscheduler.dto.SentimentWindowDto;
import com.companynews.newsscheduler.model.CompanyNews;
import com.companynews.newsscheduler.repository.CompanyNewsRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds the breakdown shown on a company's Sentiments tab: the latest article, then a series of
 * increasingly long periods.
 *
 * <h2>Why this is computed on read rather than precomputed</h2>
 * The obvious design is a nightly job that writes these numbers per company into a table. It was
 * considered and rejected. They are only ever displayed for <em>one company at a time</em>, on a tab
 * the user has to open; the arithmetic is a plain average over at most a quarter of that company's
 * stored headlines, which is a single already-loaded row and a few hundred additions. Precomputing
 * would buy nothing measurable and would cost a table, a scheduler, a backfill, and — the real
 * problem — <b>staleness</b>: a "last 1 week" figure computed at 11 PM is wrong for the whole of the
 * following day, and every boundary drifts until the next run.
 *
 * <p>The readings that <em>are</em> precomputed are latest and quarter, for a different reason: they
 * appear in tables listing fifty-plus companies at once. See
 * {@link com.companynews.newsscheduler.repository.SentimentProjection}. The rule the two cases
 * follow is not "fast versus slow" but "bulk versus single".
 *
 * <p>A useful side effect of computing on read: changing a period definition takes effect on the
 * next page load, with no rebuild of stored history.
 *
 * <h2>Today and Yesterday are calendar days</h2>
 * The remaining periods are rolling ("the last 7 × 24 hours"), but "today" and "yesterday" read as
 * calendar days to anyone looking at them, so they are: midnight to midnight in
 * {@code Asia/Kolkata}, the timezone the market and the schedulers already run on. Making them
 * rolling instead would mean the figures quietly shifted meaning through the day.
 *
 * <p>Today is genuinely empty for most companies on most days. That is correct, and it is
 * affordable only because {@link SentimentWindow#LATEST} always carries a reading when any scored
 * news exists — the two rows together cover both "what is the news right now" and "what happened
 * most recently".
 *
 * <h2>Cost, and why the timestamp field exists</h2>
 * Filtering by period needs each article as an instant. The stored {@code date} is a display string
 * in one of two formats, so deriving the instant meant a parse per article per period — several
 * passes over a quarter of history, with the previous parser throwing and catching an exception on
 * the more common of the two formats. {@link NewsItem#getPublishedAt()} holds the normalised value,
 * reducing the filter to an integer comparison; articles predating that field fall back to parsing,
 * so the tab works correctly before the backfill has run and simply costs more.
 *
 * <h2>Undated articles</h2>
 * An article whose instant cannot be determined is excluded from every dated period. It cannot be
 * placed in time, and guessing would put it in <em>all</em> of them, which is the one outcome
 * guaranteed to be wrong. Such an article can still surface as {@link SentimentWindow#LATEST}, but
 * only when nothing datable is scored at all.
 */
@Service
public class SentimentWindowService {

    private static final Logger log = LogManager.getLogger(SentimentWindowService.class);

    /** Periods are anchored to Indian market time, matching the schedulers and the cleanup job. */
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final CompanyNewsRepository repository;
    private final CurrentSentimentService currentSentimentService;
    private final SentimentScorer scorer;

    public SentimentWindowService(CompanyNewsRepository repository,
                                  CurrentSentimentService currentSentimentService,
                                  SentimentScorer scorer) {
        this.repository              = repository;
        this.currentSentimentService = currentSentimentService;
        this.scorer                  = scorer;
    }

    /**
     * Returns the full breakdown for one keyword, reading its row from the database.
     *
     * <p>Unlike the batch endpoint this deliberately loads the news array — the periods are derived
     * from individual article scores, so there is nothing to project. It is one row for one company,
     * requested only when the tab is opened.
     *
     * @param keyword company symbol
     * @return one entry per {@link SentimentWindow}, in declaration order; every row is present,
     *         carrying {@code NO_DATA} when it holds no scored article
     */
    @Transactional(readOnly = true)
    public List<SentimentWindowDto> getForKeyword(String keyword) {
        Optional<CompanyNews> record = repository.findByKeyword(keyword);
        if (record.isEmpty()) {
            log.debug("No row for keyword={} — returning empty windows", keyword);
            return emptyWindows();
        }
        return computeFrom(record.get());
    }

    /**
     * Computes the breakdown from an already-loaded record.
     *
     * @param record the news record; may be {@code null}
     * @return one entry per {@link SentimentWindow}, in declaration order, never {@code null}
     */
    public List<SentimentWindowDto> computeFrom(CompanyNews record) {
        if (record == null || record.getNews() == null || record.getNews().isEmpty()) {
            return emptyWindows();
        }

        List<NewsItem> news = record.getNews();
        ZonedDateTime now = ZonedDateTime.now(IST);

        // Today and yesterday are closed calendar days; the rest are open-ended lookbacks. All
        // bounds are resolved once here rather than per row so every row describes the same instant
        // — otherwise a request straddling midnight could report overlapping ranges.
        long startOfToday     = now.toLocalDate().atStartOfDay(IST).toInstant().toEpochMilli();
        long startOfYesterday = now.toLocalDate().minusDays(1).atStartOfDay(IST)
                                   .toInstant().toEpochMilli();

        List<SentimentWindowDto> result = new ArrayList<>(SentimentWindow.values().length);

        for (SentimentWindow window : SentimentWindow.values()) {
            result.add(switch (window) {
                case LATEST    -> latestRow(record);
                case TODAY     -> average(window, news, startOfToday, Long.MAX_VALUE);
                case YESTERDAY -> average(window, news, startOfYesterday, startOfToday);
                default        -> average(window, news,
                                          now.minusDays(window.days()).toInstant().toEpochMilli(),
                                          Long.MAX_VALUE);
            });
        }

        return result;
    }

    // ── internals ──────────────────────────────────────────────────────────────

    /**
     * Wraps the shared latest reading as a tab row.
     *
     * <p>Delegating rather than recomputing guarantees this row always matches the badge shown for
     * the same company beside its name and in the watchlist. Two independent implementations of
     * "latest" would eventually disagree, and the tab is exactly where a user would notice.
     */
    private SentimentWindowDto latestRow(CompanyNews record) {
        LatestSentimentDto latest = currentSentimentService.computeLatest(record);

        // Count is 1 when there is a reading: it rests on exactly one article, by definition.
        int count = latest.score() == null ? 0 : 1;

        return new SentimentWindowDto(SentimentWindow.LATEST.name(),
                                      SentimentWindow.LATEST.label(),
                                      latest.score(),
                                      latest.label(),
                                      count,
                                      latest.publishedAt());
    }

    /**
     * Plain average of every scored article whose publication instant falls in
     * {@code [fromMillis, toMillis)}.
     *
     * <p>The list is stored newest-first but is walked in full rather than short-circuited: a closed
     * period such as yesterday starts partway down the list, so stopping at the first out-of-range
     * item would silently truncate it.
     */
    private SentimentWindowDto average(SentimentWindow window,
                                       List<NewsItem> news,
                                       long fromMillis,
                                       long toMillis) {
        double sum = 0.0;
        int count = 0;

        for (NewsItem item : news) {
            Double score = item.getSentimentScore();
            if (score == null) continue;               // unscored: not evidence, not neutral

            Long publishedAt = publishedAtOf(item);
            if (publishedAt == null) continue;         // undated: cannot be placed in any period
            if (publishedAt < fromMillis || publishedAt >= toMillis) continue;

            sum += score;
            count++;
        }

        if (count == 0) return SentimentWindowDto.noData(window);

        double mean = round2(sum / count);
        // publishedAt stays null: an average spans many dates, and one timestamp would misrepresent
        // it. Only the latest row, which rests on a single article, carries a date.
        return new SentimentWindowDto(window.name(), window.label(),
                                      mean, scorer.toLabel(mean), count, null);
    }

    /** Every row as {@code NO_DATA}, for a company with no stored news at all. */
    private static List<SentimentWindowDto> emptyWindows() {
        List<SentimentWindowDto> rows = new ArrayList<>(SentimentWindow.values().length);
        for (SentimentWindow window : SentimentWindow.values()) {
            rows.add(SentimentWindowDto.noData(window));
        }
        return rows;
    }

    /**
     * Returns an item's publication instant, falling back to parsing its date string.
     *
     * <p>The fallback keeps the tab correct for articles stored before {@code publishedAt} existed
     * and for any the backfill has not yet reached.
     */
    private static Long publishedAtOf(NewsItem item) {
        if (item.getPublishedAt() != null) return item.getPublishedAt();
        return NewsDateParser.toEpochMillis(item.getDate());
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
