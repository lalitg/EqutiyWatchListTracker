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
 * Scores headlines that were already in the database before sentiment existed.
 *
 * <h2>Why this is needed</h2>
 * Scoring happens at save time, so only articles fetched <em>after</em> this feature ships get
 * a score. Without a backfill, every company whose news predates the deployment would display
 * NO_DATA until its next fetch cycle happens to bring in fresh articles — which for a quiet
 * company could be days.
 *
 * <h2>Why the read path does not do this lazily</h2>
 * Scoring on read is the obvious alternative and it is a trap. The batch endpoint serves the
 * watchlist and index tables, which request dozens of companies at once; scoring on demand
 * would mean hundreds of inferences plus dozens of database writes inside a single page load,
 * taking seconds and repeating on every refresh. A one-off bulk pass is both faster overall and
 * keeps writes out of the read path entirely.
 *
 * <h2>Scope</h2>
 * Only the newest {@code sentiment.current.top-n} items per company are scored, because that is
 * all Step 1 reads. Scoring every stored article would be roughly 35,000 inferences instead of
 * 11,000, for data nothing currently displays. Step 2 will need deeper history and can widen
 * this then.
 *
 * <p>The job is idempotent: already-scored items are skipped, so it can be re-run freely after
 * an interruption.
 */
@Service
public class SentimentBackfillService {

    private static final Logger log = LogManager.getLogger(SentimentBackfillService.class);

    private final CompanyNewsRepository repository;
    private final SentimentScorer scorer;
    private final KeywordLoader keywordLoader;
    private final int topN;

    /** Guards against two backfills running at once and doubling the inference load. */
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SentimentBackfillService(CompanyNewsRepository repository,
                                    SentimentScorer scorer,
                                    KeywordLoader keywordLoader,
                                    @Value("${sentiment.current.top-n:5}") int topN) {
        this.repository    = repository;
        this.scorer        = scorer;
        this.keywordLoader = keywordLoader;
        this.topN          = topN;
    }

    /** Whether a backfill is currently in progress. */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Starts a backfill on a background thread and returns immediately.
     *
     * <p>Runs off the request thread because a full pass over ~2,200 companies takes minutes —
     * far longer than any sensible HTTP timeout.
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
     * Scores the newest unscored headlines for every company keyword.
     *
     * <p>Each company is committed in its own transaction so an interruption leaves completed
     * work persisted rather than rolling back the entire pass.
     *
     * @return the number of headlines newly scored
     */
    public int runBackfill() {
        Set<String> symbols = keywordLoader.loadCompanySymbols();
        log.info("Sentiment backfill starting for {} company keywords (top {} items each)",
                 symbols.size(), topN);

        long startMs = System.currentTimeMillis();
        int scoredTotal = 0;
        int companiesTouched = 0;
        int failures = 0;

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
        }

        long seconds = (System.currentTimeMillis() - startMs) / 1000;
        log.info("Sentiment backfill complete — scored {} headlines across {} companies "
               + "({} failures) in {}s", scoredTotal, companiesTouched, failures, seconds);
        return scoredTotal;
    }

    /**
     * Scores the newest unscored items for one keyword and persists them.
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
        int examined = 0;

        for (NewsItem item : updated) {
            if (examined >= topN) break;
            examined++;

            if (item.getSentimentScore() != null) continue;   // idempotent: already done

            scorer.score(item);
            if (item.getSentimentScore() != null) scored++;
        }

        if (scored == 0) return 0;

        record.setNews(updated);
        // last_updated is deliberately NOT touched: backfilling a score is not new news, and
        // bumping the timestamp would make the frontend claim fresh articles that do not exist.
        repository.save(record);

        log.debug("Backfilled {} score(s) for keyword={}", scored, keyword);
        return scored;
    }
}
