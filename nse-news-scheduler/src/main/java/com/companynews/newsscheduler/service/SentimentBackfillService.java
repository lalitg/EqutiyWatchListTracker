package com.companynews.newsscheduler.service;

import com.companynews.newsscheduler.dto.NewsItem;
import com.companynews.newsscheduler.model.CompanyNews;
import com.companynews.newsscheduler.repository.CompanyNewsRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Brings existing rows up to date with everything the sentiment feature needs: a score on every
 * headline, a normalised publication instant, and populated denormalised columns.
 *
 * <h2>Why this is needed</h2>
 * All three are written at save time, so only articles fetched <em>after</em> a given change
 * shipped will have them. Without a backfill:
 * <ul>
 *   <li>every headline stored before scoring existed stays unscored, so the historical windows
 *       average over a fraction of the history that is actually there;</li>
 *   <li>every row keeps a {@code null} {@code current_score} column, and because the batch
 *       endpoint now reads those columns rather than the JSONB, <b>every company shows NO_DATA in
 *       the watchlist and index tables until its next fetch</b> — which for a quiet company can be
 *       days. This is the one that makes running the backfill mandatory rather than merely
 *       advisable after deploying.</li>
 * </ul>
 *
 * <h2>Why the read path does not do this lazily</h2>
 * Scoring on read is the obvious alternative and it is a trap. The batch endpoint serves the
 * watchlist and index tables, which request dozens of companies at once; scoring on demand would
 * mean hundreds of inferences plus dozens of database writes inside a single page load, taking
 * seconds and repeating on every refresh. A one-off bulk pass is both faster overall and keeps
 * writes out of the read path entirely.
 *
 * <h2>Scope: all stored articles, not just the newest few</h2>
 * This job originally scored only the newest {@code top-n} items per company, because that was
 * all the current-sentiment reading looked at. The Sentiments tab averages over windows reaching
 * back a quarter, so anything left unscored is silently missing from those averages — and missing
 * asymmetrically, since it is always the older articles that are absent. The pass therefore scores
 * every stored item.
 *
 * <p>{@code sentiment.backfill.max-per-keyword} caps the work per company for operators who want
 * to bound a first run; {@code 0}, the default, means no cap. Capping trades completeness in the
 * longer windows for a shorter run, so it is a deliberate choice rather than a default.
 *
 * <p>The job is idempotent: already-scored items and items that already carry an instant are
 * skipped, so it can be re-run freely after an interruption and costs almost nothing on a second
 * pass.
 */
@Service
public class SentimentBackfillService {

    private static final Logger log = LogManager.getLogger(SentimentBackfillService.class);

    /** How often to log progress, in companies. A full pass takes minutes; silence looks hung. */
    private static final int PROGRESS_EVERY = 100;

    private final CompanyNewsRepository repository;
    private final SentimentScorer scorer;
    private final KeywordLoader keywordLoader;
    private final CurrentSentimentService currentSentimentService;

    /** Per-company cap on headlines scored in one pass; {@code 0} means no cap. */
    private final int maxPerKeyword;

    /** Guards against two backfills running at once and doubling the inference load. */
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SentimentBackfillService(CompanyNewsRepository repository,
                                    SentimentScorer scorer,
                                    KeywordLoader keywordLoader,
                                    CurrentSentimentService currentSentimentService,
                                    @Value("${sentiment.backfill.max-per-keyword:0}") int maxPerKeyword) {
        this.repository    = repository;
        this.scorer        = scorer;
        this.keywordLoader = keywordLoader;
        this.currentSentimentService = currentSentimentService;
        this.maxPerKeyword = maxPerKeyword;
    }

    /** Whether a backfill is currently in progress. */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Starts a backfill on a background thread and returns immediately.
     *
     * <p>Runs off the request thread because a full pass over every company takes minutes — far
     * longer than any sensible HTTP timeout.
     *
     * @return {@code true} if a run was started, {@code false} if one was already in progress
     */
    public boolean startAsync() {
        if (!scorer.isAvailable()) {
            log.warn("Backfill requested but the sentiment model is unavailable — nothing to do");
            return false;
        }
        if (!running.compareAndSet(false, true)) {
            log.warn("Backfill requested but one is already running — ignoring");
            return false;
        }

        Thread worker = new Thread(() -> {
            try {
                runBackfill();
            } finally {
                running.set(false);
            }
        }, "sentiment-backfill");
        worker.setDaemon(true);
        worker.start();
        return true;
    }

    /**
     * Scores unscored headlines and refreshes denormalised columns for every company keyword.
     *
     * <p>Each company is committed in its own transaction so an interruption leaves completed work
     * persisted rather than rolling back the entire pass.
     *
     * @return the number of headlines newly scored
     */
    public int runBackfill() {
        Set<String> symbols = keywordLoader.loadCompanySymbols();
        log.info("Sentiment backfill starting for {} company keywords ({})",
                 symbols.size(),
                 maxPerKeyword > 0 ? "max " + maxPerKeyword + " items each" : "all stored items");

        long startMs = System.currentTimeMillis();
        int scoredTotal = 0;
        int companiesTouched = 0;
        int failures = 0;
        int processed = 0;

        for (String symbol : symbols) {
            try {
                int scored = backfillKeyword(symbol);
                if (scored > 0) {
                    scoredTotal += scored;
                    companiesTouched++;
                }
            } catch (Exception e) {
                failures++;
                log.error("Backfill failed for keyword={}: {}", symbol, e.getMessage());
            }

            if (++processed % PROGRESS_EVERY == 0) {
                log.info("Backfill progress — {}/{} keywords, {} headlines scored so far",
                         processed, symbols.size(), scoredTotal);
            }
        }

        long seconds = (System.currentTimeMillis() - startMs) / 1000;
        log.info("Sentiment backfill complete — scored {} headlines across {} companies "
               + "({} failures) in {}s", scoredTotal, companiesTouched, failures, seconds);
        return scoredTotal;
    }

    /**
     * Brings one keyword fully up to date and persists it.
     *
     * <p>Three things can make a row need writing, and any one of them is enough:
     * <ol>
     *   <li>an item has no sentiment score;</li>
     *   <li>an item has no {@code publishedAt} instant, though its date string parses;</li>
     *   <li>the row has never had its denormalised sentiment columns written — true of every row
     *       that predates them, and the reason a row with nothing else to do is still saved.</li>
     * </ol>
     *
     * @param keyword the company symbol to backfill
     * @return how many headlines were newly scored
     */
    @Transactional
    public int backfillKeyword(String keyword) {
        Optional<CompanyNews> existing = repository.findByKeyword(keyword);
        if (existing.isEmpty()) return 0;

        CompanyNews record = existing.get();
        List<NewsItem> news = record.getNews();
        if (news == null || news.isEmpty()) return 0;

        // Work on a copy so Hibernate sees a genuinely new JSONB value to write back.
        List<NewsItem> updated = new ArrayList<>(news);

        int scored = 0;
        boolean itemsChanged = false;

        for (NewsItem item : updated) {
            if (item.getPublishedAt() == null) {
                Long publishedAt = NewsDateParser.toEpochMillis(item.getDate());
                if (publishedAt != null) {
                    item.setPublishedAt(publishedAt);
                    itemsChanged = true;
                }
            }

            if (item.getSentimentScore() != null) continue;         // idempotent: already done
            if (maxPerKeyword > 0 && scored >= maxPerKeyword) continue;

            scorer.score(item);
            if (item.getSentimentScore() != null) {
                scored++;
                itemsChanged = true;
            }
        }

        // A row whose items are all up to date may still be missing its denormalised columns,
        // in which case the batch endpoint would report NO_DATA for it forever.
        boolean columnsUnset = record.getLatestLabel() == null;
        if (!itemsChanged && !columnsUnset) return 0;

        record.setNews(updated);
        currentSentimentService.refresh(record);
        // last_updated is deliberately NOT touched: backfilling a score is not new news, and
        // bumping the timestamp would make the frontend claim fresh articles that do not exist.
        repository.save(record);

        log.debug("Backfilled keyword={} — {} newly scored, columnsUnset={}",
                  keyword, scored, columnsUnset);
        return scored;
    }
}
