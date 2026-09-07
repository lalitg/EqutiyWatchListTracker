package com.companynews.newsscheduler.service;

import com.companynews.newsscheduler.dto.NewsItem;
import com.companynews.newsscheduler.model.CompanyNews;
import com.companynews.newsscheduler.repository.CompanyNewsRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Core service responsible for deduplicating and persisting news items to the database.
 *
 * <p>Applies a 4-layer deduplication strategy before saving:
 * <ol>
 *   <li><b>Null/empty link</b> — items with no URL are unusable and skipped immediately.</li>
 *   <li><b>Exact URL match against DB</b> — URLs already stored in the {@code news} JSONB
 *       column for this keyword are skipped.</li>
 *   <li><b>Exact URL match within current batch</b> — prevents saving the same URL twice
 *       if it appears multiple times in a single fetch result.</li>
 *   <li><b>Jaccard similarity check</b> — headlines too similar (≥ 60% token overlap) to any
 *       existing stored headline are skipped to avoid near-duplicate articles.</li>
 * </ol>
 *
 * <p>Per-keyword locking via {@link ReentrantLock} ensures that two threads saving news for
 * the same keyword do not race. Threads saving different keywords are never blocked by each other.
 *
 * <p>Micrometer counters track items saved and skipped (by reason) for production monitoring.
 */
@Service
public class NewsWorker {

    private static final Logger log = LogManager.getLogger(NewsWorker.class);

    private final CompanyNewsRepository repository;
    private final SimilarityChecker similarityChecker;
    private final NewsStore newsStore;
    private final NewsImportanceClassifier importanceClassifier;

    /**
     * Assigns each newly-accepted company headline a sentiment score.
     *
     * <p>Runs at save time rather than on read so each headline costs exactly one inference
     * for its lifetime. The watchlist and index tables render dozens of companies per page
     * view; scoring on read would repeat that work on every refresh.
     */
    private final SentimentScorer sentimentScorer;

    /**
     * Keeps the row's denormalised sentiment columns in step with the article list.
     *
     * <p>Refreshing here, inside the same transaction that writes the articles, is what lets the
     * batch endpoint read those columns without opening the JSONB. Doing it on a schedule instead
     * would leave a window in which the column and the array disagree, and the tables would serve
     * a sentiment computed from articles that are no longer the newest ones.
     */
    private final CurrentSentimentService currentSentimentService;

    /**
     * Per-keyword {@link ReentrantLock} map.
     *
     * <p>Thread 1 saving {@code INFY} does NOT block Thread 2 saving {@code RELIANCE}.
     * {@link ConcurrentHashMap#computeIfAbsent} is atomic — no external synchronization needed
     * for lock creation.
     */
    private final ConcurrentHashMap<String, ReentrantLock> keywordLocks = new ConcurrentHashMap<>();

    /**
     * Micrometer counter tracking the total number of news items written to the database.
     * Useful for alerting on "0 items saved in last N minutes" (silent pipeline detection).
     */
    private final Counter savedCounter;

    /**
     * Micrometer counter tracking items skipped due to duplicate URL (DB match or batch match).
     */
    private final Counter skippedUrlCounter;

    /**
     * Micrometer counter tracking items skipped due to near-duplicate headline (Jaccard similarity).
     */
    private final Counter skippedSimilarityCounter;

    /**
     * Micrometer counter tracking items skipped because their link URL was null or empty.
     */
    private final Counter skippedNullLinkCounter;

    /**
     * Constructs a {@code NewsWorker} with all required dependencies injected by Spring.
     *
     * @param repository           repository for reading and writing {@link CompanyNews} records
     * @param similarityChecker    Jaccard similarity checker for near-duplicate headline detection
     * @param newsStore            in-memory mirror of {@code company_news}
     * @param importanceClassifier flags company headlines as important corporate-action news
     * @param sentimentScorer      scores each newly-accepted company headline
     * @param currentSentimentService refreshes the denormalised sentiment columns on every save
     * @param meterRegistry        Micrometer registry for registering production counters
     */
    public NewsWorker(CompanyNewsRepository repository,
                      SimilarityChecker similarityChecker,
                      NewsStore newsStore,
                      NewsImportanceClassifier importanceClassifier,
                      SentimentScorer sentimentScorer,
                      CurrentSentimentService currentSentimentService,
                      MeterRegistry meterRegistry) {
        this.repository           = repository;
        this.similarityChecker    = similarityChecker;
        this.newsStore            = newsStore;
        this.importanceClassifier = importanceClassifier;
        this.sentimentScorer      = sentimentScorer;
        this.currentSentimentService = currentSentimentService;
        this.savedCounter        = Counter.builder("news.items.saved")
            .description("Total news items written to DB")
            .register(meterRegistry);
        this.skippedUrlCounter   = Counter.builder("news.items.skipped")
            .tag("reason", "url_duplicate")
            .description("Items skipped due to exact URL already in DB or current batch")
            .register(meterRegistry);
        this.skippedSimilarityCounter = Counter.builder("news.items.skipped")
            .tag("reason", "similarity")
            .description("Items skipped due to near-duplicate headline")
            .register(meterRegistry);
        this.skippedNullLinkCounter   = Counter.builder("news.items.skipped")
            .tag("reason", "null_link")
            .description("Items skipped because link was null or empty")
            .register(meterRegistry);
    }

    /**
     * Returns the {@link ReentrantLock} for the given keyword, creating one if absent.
     *
     * @param keyword the keyword to get or create a lock for
     * @return the {@link ReentrantLock} associated with this keyword
     */
    private ReentrantLock getLock(String keyword) {
        return keywordLocks.computeIfAbsent(keyword, k -> new ReentrantLock());
    }

    /**
     * Deduplicates the given news items and saves new ones to the database for the given keyword.
     *
     * <p>The 4-layer deduplication (null check → DB URL match → batch URL match → Jaccard similarity)
     * is applied inside the per-keyword lock so concurrent calls for the same keyword do not
     * produce race conditions or duplicate DB inserts.
     *
     * <p>After dedup, items are sorted newest-first. The hourly cleanup job handles removing
     * articles outside the 24-hour retention window.
     *
     * <p>Log4j2's {@link ThreadContext} is populated with the keyword so all log lines inside
     * this method carry the {@code keyword} field, making parallel log output traceable in
     * log aggregators without needing to grep by thread name.
     *
     * <p>If a {@link DataIntegrityViolationException} is thrown (concurrent insert race),
     * {@link #upsert(String, List)} is called as a fallback to re-read and merge.
     *
     * <p>When {@code isCompany} is {@code true}, each newly-accepted item's headline is run
     * through {@link NewsImportanceClassifier} and tagged {@code category="important"} if it
     * matches a corporate-action phrase. Classification is skipped entirely for sectors and
     * macro keywords so their behavior is unchanged.
     *
     * @param keyword   the keyword to save news under (company symbol, sector, or macro term)
     * @param newItems  the list of candidate news items to deduplicate and save
     * @param isCompany whether {@code keyword} is a company symbol — only then are items
     *                  classified for the "Important News" tab
     */
    @Transactional
    public void saveNews(String keyword, List<NewsItem> newItems, boolean isCompany) {
        if (newItems == null || newItems.isEmpty()) return;

        ReentrantLock lock = getLock(keyword);
        lock.lock();
        ThreadContext.put("keyword", keyword);

        try {
            Optional<CompanyNews> existing = repository.findByKeyword(keyword);
            CompanyNews record;
            List<NewsItem> currentNews;

            if (existing.isPresent()) {
                record = existing.get();
                currentNews = record.getNews() != null
                    ? new ArrayList<>(record.getNews())
                    : new ArrayList<>();
            } else {
                record = new CompanyNews();
                record.setKeyword(keyword);
                record.setSentiments("");
                currentNews = new ArrayList<>();
            }

            // Build URL lookup set for dedup layer 2
            Set<String> savedUrls = currentNews.stream()
                .map(NewsItem::getLink)
                .filter(l -> l != null)
                .collect(Collectors.toSet());

            Set<String> seenInBatch = new HashSet<>();
            int added = 0;

            for (NewsItem item : newItems) {
                String link    = item.getLink();
                String summary = item.getSummary();

                // Layer 1: null/empty link check
                if (link == null || link.isEmpty()) {
                    log.debug("Skipping item with null/empty link — summary: {}", summary);
                    skippedNullLinkCounter.increment();
                    continue;
                }
                // Layer 2: exact URL match against DB
                if (savedUrls.contains(link)) {
                    log.debug("Duplicate URL already in DB — skipping: {}", link);
                    skippedUrlCounter.increment();
                    continue;
                }
                // Layer 3: exact URL match within current batch
                if (seenInBatch.contains(link)) {
                    log.debug("Duplicate URL seen in current batch — skipping: {}", link);
                    skippedUrlCounter.increment();
                    continue;
                }
                // Layer 4: time-aware Jaccard similarity check against already-accepted items
                if (similarityChecker.isDuplicate(item, currentNews)) {
                    log.debug("Near-duplicate headline skipped (Jaccard): {}", summary);
                    skippedSimilarityCounter.increment();
                    continue;
                }

                // Layer 5 (company keywords only): flag important corporate-action headlines
                // for the "Important News" tab, and assign a sentiment score. Both run only
                // for company symbols — sectors and macro keywords are left untouched.
                //
                // Scoring sits here, after every dedup layer, so a headline is only ever
                // scored if it is actually being stored. Running it earlier would waste
                // inference on the majority of items, which are duplicates.
                if (isCompany) {
                    item.setCategory(importanceClassifier.classify(summary));
                    sentimentScorer.score(item);
                }

                // Normalise the date string to an instant once, here, for every keyword type.
                // Retention, sorting and the sentiment windows all need it as a number, and
                // deriving it on demand meant re-parsing the whole stored array on every pass.
                item.setPublishedAt(NewsDateParser.toEpochMillis(item.getDate()));

                currentNews.add(item);
                savedUrls.add(link);
                seenInBatch.add(link);
                added++;
            }

            if (added == 0) {
                log.debug("No new items to save after deduplication for keyword: {}", keyword);
                // Articles passed UrlWindow but were all blocked by DB URL check or Jaccard.
                // Still touch last_updated so the frontend doesn't show a stale timestamp
                // for runs where old article URLs cycled back through an evicted UrlWindow.
                if (existing.isPresent()) {
                    record.setLastUpdated(LocalDateTime.now());
                    repository.save(record);
                }
                return;
            }

            // Sort latest first — the hourly cleanup job handles removing articles outside the retention window
            currentNews.sort((a, b) -> compareItems(b, a));

            record.setNews(currentNews);
            // Refresh before saving so the denormalised columns and the JSONB go out in the same
            // statement. The article list has just changed, so the stored reading is stale by
            // definition, and CurrentSentimentService reads newest-first order — which the sort
            // above has only now established.
            currentSentimentService.refresh(record);
            record.setLastUpdated(LocalDateTime.now());
            repository.save(record);
            newsStore.put(keyword, currentNews);

            savedCounter.increment(added);
            log.info("Saved {} total item(s) for keyword: {} ({} new this run)",
                    currentNews.size(), keyword, added);

        } catch (DataIntegrityViolationException e) {
            log.warn("Constraint violation on save — falling back to upsert for keyword: {}", keyword);
            upsert(keyword, newItems, isCompany);
        } finally {
            ThreadContext.remove("keyword");
            lock.unlock();
        }
    }

    /**
     * Updates {@code last_updated} to now for the given keyword without changing its news items.
     *
     * <p>Called when Google RSS is fetched successfully but all articles are already in the
     * {@link UrlWindow} (no new URLs this run). Touching the timestamp signals to the frontend
     * that the data was verified recently — preventing stale-looking timestamps when Google
     * keeps the same articles in its feed for multiple days.
     *
     * @param keyword the keyword whose timestamp should be refreshed
     */
    @Transactional
    public void touchLastUpdated(String keyword) {
        repository.findByKeyword(keyword).ifPresent(record -> {
            record.setLastUpdated(LocalDateTime.now());
            repository.save(record);
            log.debug("Refreshed last_updated for keyword={} (no new items this run)", keyword);
        });
    }

    /**
     * Fallback for concurrent insert races: re-reads the row and merges new items in.
     *
     * <p>Called when {@link #saveNews} finds no existing row but another thread inserts one
     * between the {@code findByKeyword} and {@code save} calls. Re-reading guarantees we
     * merge onto the latest state rather than overwriting it.
     *
     * <p>WHY no {@code @Transactional} here: this method is called from {@link #saveNews}
     * which is itself {@code @Transactional}. Spring's AOP proxy intercepts only external
     * calls — calling {@code upsert()} via {@code this.upsert()} bypasses the proxy entirely,
     * so adding {@code @Transactional} here would have no effect. The method already
     * participates in the caller's transaction via Spring's default REQUIRED propagation.
     *
     * @param keyword   the keyword whose row encountered a concurrent insert
     * @param newItems  the items to merge into the existing row
     * @param isCompany whether {@code keyword} is a company symbol — only then are items classified
     */
    private void upsert(String keyword, List<NewsItem> newItems, boolean isCompany) {
        Optional<CompanyNews> existing = repository.findByKeyword(keyword);
        if (existing.isEmpty()) {
            log.warn("Upsert: row still not found for keyword: {} — giving up", keyword);
            return;
        }

        CompanyNews record = existing.get();
        List<NewsItem> currentNews = record.getNews() != null
            ? new ArrayList<>(record.getNews())
            : new ArrayList<>();

        for (NewsItem item : newItems) {
            if (isCompany) {
                item.setCategory(importanceClassifier.classify(item.getSummary()));
                sentimentScorer.score(item);
            }
            item.setPublishedAt(NewsDateParser.toEpochMillis(item.getDate()));
            currentNews.add(0, item);
        }

        record.setNews(currentNews);
        currentSentimentService.refresh(record);
        record.setLastUpdated(LocalDateTime.now());
        repository.save(record);
        log.info("Upsert succeeded for keyword: {}", keyword);
    }

    /**
     * Compares two items chronologically, null-safe, with items of unknown date sorted oldest.
     *
     * <p>Prefers the stored {@code publishedAt} instant and falls back to parsing the date string
     * for items written before that field existed. The list being sorted mixes both kinds in the
     * period after deployment and before the backfill finishes, so the fallback is on the live
     * path rather than a defensive nicety.
     *
     * <p>Two items whose dates are both unparseable are ordered lexicographically by date string,
     * preserving the previous behaviour and keeping the sort deterministic.
     *
     * @param a the first item
     * @param b the second item
     * @return negative if {@code a} is older than {@code b}, positive if newer, 0 if equal
     */
    private int compareItems(NewsItem a, NewsItem b) {
        Long instantA = instantOf(a);
        Long instantB = instantOf(b);
        if (instantA != null && instantB != null) return Long.compare(instantA, instantB);
        if (instantA != null) return 1;      // b has no usable date — treat it as the older one
        if (instantB != null) return -1;

        String dateA = a == null ? null : a.getDate();
        String dateB = b == null ? null : b.getDate();
        if (dateA == null && dateB == null) return 0;
        if (dateA == null) return -1;
        if (dateB == null) return 1;
        return dateA.compareTo(dateB);
    }

    /** Publication instant for an item, parsing its date string only if the field is absent. */
    private static Long instantOf(NewsItem item) {
        if (item == null) return null;
        if (item.getPublishedAt() != null) return item.getPublishedAt();
        return NewsDateParser.toEpochMillis(item.getDate());
    }
}
