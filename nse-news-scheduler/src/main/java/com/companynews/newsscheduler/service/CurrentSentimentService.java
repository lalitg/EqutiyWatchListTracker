package com.companynews.newsscheduler.service;

import com.companynews.newsscheduler.dto.CompanySentimentDto;
import com.companynews.newsscheduler.dto.LatestSentimentDto;
import com.companynews.newsscheduler.dto.NewsItem;
import com.companynews.newsscheduler.dto.SentimentDto;
import com.companynews.newsscheduler.dto.SentimentWindow;
import com.companynews.newsscheduler.model.CompanyNews;
import com.companynews.newsscheduler.repository.CompanyNewsRepository;
import com.companynews.newsscheduler.repository.SentimentProjection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Produces the two headline sentiment readings for a company: the <b>latest</b> article and the
 * <b>90-day average</b>.
 *
 * <h2>This class never runs the model</h2>
 * Headlines are scored once, at save time, by {@link SentimentScorer} inside {@link NewsWorker}.
 * This service only reads those stored numbers. That split is load-bearing: the watchlist and the
 * index / sector tables render dozens of companies per page view, so scoring on read would mean
 * hundreds of inferences every time somebody opens a table.
 *
 * <h2>Why two readings rather than one</h2>
 * A single figure cannot answer both questions a reader has. The latest reading is the reaction to
 * the most recent piece of news; the quarter is the backdrop it landed against. They routinely
 * disagree, and that disagreement is the signal.
 *
 * <h2>Latest is one article, and is not age-limited</h2>
 * It is the newest headline that carries a score — not an average, and not suppressed for being
 * old. An earlier design averaged the newest five headlines and called the result "current", which
 * needed an expiry rule: nothing rewrites a quiet company's score, so that average would have kept
 * reading as current indefinitely.
 *
 * <p>This reading needs no such rule because it makes no claim to be current. It says what the last
 * piece of news said, which stays true however long ago it was. The obligation the expiry rule was
 * discharging — never letting stale data pass as fresh — is met instead by carrying the article's
 * date through to the UI. Hence {@link LatestSentimentDto#publishedAt()}.
 *
 * <p>It is selected by maximum publication instant rather than by list position. The stored array is
 * kept newest-first, but items whose date parses in neither format sort to the end, so trusting
 * position would occasionally pick the wrong article.
 *
 * <h2>Unscored articles</h2>
 * Items with a {@code null} score are skipped, never treated as neutral. Conflating "not scored"
 * with "neutral" would drag averages toward zero for reasons unrelated to the news, and would make
 * a scoring outage look like a market view. A company whose newest headline failed to score falls
 * through to the next one down rather than reporting nothing.
 *
 * <h2>Where the answers are stored</h2>
 * {@link #refresh(CompanyNews)} writes both readings onto the row's denormalised columns wherever
 * the article list changes. Batch reads then come from
 * {@link CompanyNewsRepository#findSentimentsByKeywordIn} without loading the JSONB at all — see
 * {@link SentimentProjection}.
 */
@Service
public class CurrentSentimentService {

    private static final Logger log = LogManager.getLogger(CurrentSentimentService.class);

    /**
     * Length of the aggregate window, taken from the enum the Sentiments tab uses.
     *
     * <p>Read from {@link SentimentWindow#QUARTER_1} rather than declared here so the column and the
     * tab row can never drift apart: they are the same number by construction, not by agreement.
     */
    private static final long QUARTER_MILLIS =
        Duration.ofDays(SentimentWindow.QUARTER_1.days()).toMillis();

    private final CompanyNewsRepository repository;
    private final SentimentScorer scorer;

    public CurrentSentimentService(CompanyNewsRepository repository, SentimentScorer scorer) {
        this.repository = repository;
        this.scorer     = scorer;
    }

    /**
     * Returns both readings for a single keyword, from the denormalised columns.
     *
     * @param keyword company symbol, sector name, or macro term
     * @return the readings, or {@link CompanySentimentDto#noData()} when there is nothing to report
     */
    @Transactional(readOnly = true)
    public CompanySentimentDto getForKeyword(String keyword) {
        List<SentimentProjection> rows = repository.findSentimentsByKeywordIn(List.of(keyword));
        return rows.isEmpty() ? CompanySentimentDto.noData() : fromProjection(rows.get(0));
    }

    /**
     * Returns both readings for many keywords using a single database query.
     *
     * <p>This is the endpoint behind the watchlist and the index / sector company tables. Every
     * requested keyword appears in the result, including ones with no row at all — the caller
     * receives {@link CompanySentimentDto#noData()} for those rather than having to reason about
     * absent map keys.
     *
     * @param keywords the keywords to look up
     * @return an insertion-ordered map of keyword to readings, one entry per requested keyword
     */
    @Transactional(readOnly = true)
    public Map<String, CompanySentimentDto> getForKeywords(List<String> keywords) {
        Map<String, CompanySentimentDto> result = new LinkedHashMap<>();
        if (keywords == null || keywords.isEmpty()) return result;

        // Seed with NO_DATA so keywords without a row still appear in the response.
        for (String keyword : keywords) {
            result.put(keyword, CompanySentimentDto.noData());
        }

        for (SentimentProjection row : repository.findSentimentsByKeywordIn(keywords)) {
            result.put(row.getKeyword(), fromProjection(row));
        }

        return result;
    }

    /**
     * Recomputes both readings and writes them onto the record's denormalised columns.
     *
     * <p>Called wherever the article list changes — {@link NewsWorker} on save,
     * {@link NewsCleanupService} when retention drops articles, and
     * {@link SentimentBackfillService} when historical items gain scores. The caller persists the
     * record; refreshing here rather than in a scheduled job is what keeps the columns from ever
     * disagreeing with the array they summarise.
     *
     * @param record the record to refresh; may be {@code null}
     * @return the readings as they would now be served
     */
    public CompanySentimentDto refresh(CompanyNews record) {
        if (record == null) return CompanySentimentDto.noData();

        LatestSentimentDto latest  = computeLatest(record);
        SentimentDto       quarter = computeQuarter(record);

        record.setLatestScore(latest.score());
        record.setLatestLabel(latest.label());
        record.setNewestArticleAt(latest.publishedAt());
        record.setQuarterScore(quarter.score());
        record.setQuarterLabel(quarter.label());
        record.setQuarterCount(quarter.articleCount());

        return new CompanySentimentDto(latest, quarter);
    }

    /**
     * Recomputes both readings and reports whether anything actually moved.
     *
     * <p>Exists for {@link NewsCleanupService}. The quarter average changes with the passage of time
     * rather than with new data — articles fall out of the ninety-day window while a company sits
     * silent — so a row that never changes would never be refreshed and its stored average would
     * drift. Recomputing every row hourly fixes that, but blindly saving every row would rewrite the
     * whole JSONB blob of every company every hour for nothing.
     *
     * <p>Computing is nearly free where this is called (cleanup has already loaded every row);
     * the write is the expensive part. So this reports whether a write is warranted.
     *
     * @param record the record to refresh; may be {@code null}
     * @return {@code true} if any denormalised value changed and the row is worth saving
     */
    public boolean refreshIfChanged(CompanyNews record) {
        if (record == null) return false;

        Double  previousLatestScore   = record.getLatestScore();
        String  previousLatestLabel   = record.getLatestLabel();
        Long    previousNewestAt      = record.getNewestArticleAt();
        Double  previousQuarterScore  = record.getQuarterScore();
        String  previousQuarterLabel  = record.getQuarterLabel();
        Integer previousQuarterCount  = record.getQuarterCount();

        refresh(record);

        return !Objects.equals(previousLatestScore,  record.getLatestScore())
            || !Objects.equals(previousLatestLabel,  record.getLatestLabel())
            || !Objects.equals(previousNewestAt,     record.getNewestArticleAt())
            || !Objects.equals(previousQuarterScore, record.getQuarterScore())
            || !Objects.equals(previousQuarterLabel, record.getQuarterLabel())
            || !Objects.equals(previousQuarterCount, record.getQuarterCount());
    }

    /**
     * Computes both readings from an already-loaded record, without touching the database.
     *
     * @param record the news record; may be {@code null}
     * @return the readings, never {@code null}
     */
    public CompanySentimentDto computeFrom(CompanyNews record) {
        return new CompanySentimentDto(computeLatest(record), computeQuarter(record));
    }

    /**
     * Returns the sentiment of the most recent scored headline.
     *
     * <p>Selected by maximum publication instant, not by position in the stored array. Undated items
     * are eligible only as a last resort: an article whose date parses in neither format cannot be
     * compared against the others, so it is used only when nothing else is scored at all.
     *
     * @param record the news record; may be {@code null}
     * @return the reading, or {@link LatestSentimentDto#noData()} when nothing is scored
     */
    public LatestSentimentDto computeLatest(CompanyNews record) {
        if (record == null || record.getNews() == null || record.getNews().isEmpty()) {
            return LatestSentimentDto.noData();
        }

        NewsItem best = null;
        long bestAt = Long.MIN_VALUE;
        NewsItem undatedFallback = null;

        for (NewsItem item : record.getNews()) {
            if (item.getSentimentScore() == null) continue;

            Long publishedAt = publishedAtOf(item);
            if (publishedAt == null) {
                // Keep the first one seen. The array is maintained newest-first, so among items we
                // cannot place in time this is the best guess available.
                if (undatedFallback == null) undatedFallback = item;
                continue;
            }
            if (publishedAt > bestAt) {
                bestAt = publishedAt;
                best   = item;
            }
        }

        if (best != null) {
            return new LatestSentimentDto(best.getSentimentScore(), best.getSentimentLabel(), bestAt);
        }
        if (undatedFallback != null) {
            return new LatestSentimentDto(undatedFallback.getSentimentScore(),
                                          undatedFallback.getSentimentLabel(), null);
        }

        log.debug("No scored articles for keyword={} — latest reports NO_DATA", record.getKeyword());
        return LatestSentimentDto.noData();
    }

    /**
     * Computes the plain average across every scored headline published in the last 90 days.
     *
     * <p>Undated articles are excluded. They cannot be placed in time, and assuming they belong
     * would silently pad the average with articles of unknown age.
     *
     * @param record the news record; may be {@code null}
     * @return the average, or {@link SentimentDto#noData()} when the quarter holds no scored article
     */
    public SentimentDto computeQuarter(CompanyNews record) {
        if (record == null || record.getNews() == null || record.getNews().isEmpty()) {
            return SentimentDto.noData();
        }

        long cutoff = System.currentTimeMillis() - QUARTER_MILLIS;

        double sum = 0.0;
        int count = 0;

        for (NewsItem item : record.getNews()) {
            Double score = item.getSentimentScore();
            if (score == null) continue;

            Long publishedAt = publishedAtOf(item);
            if (publishedAt == null || publishedAt < cutoff) continue;

            sum += score;
            count++;
        }

        if (count == 0) return SentimentDto.noData();

        double mean = round2(sum / count);
        return new SentimentDto(mean, scorer.toLabel(mean), count);
    }

    // ── internals ──────────────────────────────────────────────────────────────

    private CompanySentimentDto fromProjection(SentimentProjection row) {
        LatestSentimentDto latest = row.getLatestScore() == null
            ? LatestSentimentDto.noData()
            : new LatestSentimentDto(row.getLatestScore(),
                                     row.getLatestLabel() != null
                                         ? row.getLatestLabel()
                                         : scorer.toLabel(row.getLatestScore()),
                                     row.getNewestArticleAt());

        SentimentDto quarter = row.getQuarterScore() == null
            ? SentimentDto.noData()
            : new SentimentDto(row.getQuarterScore(),
                               row.getQuarterLabel() != null
                                   ? row.getQuarterLabel()
                                   : scorer.toLabel(row.getQuarterScore()),
                               row.getQuarterCount() == null ? 0 : row.getQuarterCount());

        return new CompanySentimentDto(latest, quarter);
    }

    /**
     * Returns an item's publication instant, falling back to parsing its date string.
     *
     * <p>The fallback covers articles stored before {@code publishedAt} existed and any the backfill
     * has not reached yet.
     */
    private static Long publishedAtOf(NewsItem item) {
        if (item.getPublishedAt() != null) return item.getPublishedAt();
        return NewsDateParser.toEpochMillis(item.getDate());
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
