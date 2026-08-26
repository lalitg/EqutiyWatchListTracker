package com.companynews.newsscheduler.service;

import com.companynews.newsscheduler.dto.NewsItem;
import com.companynews.newsscheduler.dto.SentimentDto;
import com.companynews.newsscheduler.model.CompanyNews;
import com.companynews.newsscheduler.repository.CompanyNewsRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Computes a keyword's <b>current</b> sentiment from the scores already stored on its news items.
 *
 * <h2>This class never runs the model</h2>
 * Headlines are scored once, at save time, by {@link SentimentScorer} inside
 * {@link NewsWorker}. This service only reads those stored numbers and averages them. That
 * split is deliberate and load-bearing: the watchlist and the Nifty index / sector tables
 * render dozens of companies per page view, so scoring on read would mean hundreds of
 * inferences every time somebody opens a table. Reading stored values keeps those endpoints
 * as cheap as they are today.
 *
 * <h2>What "current" means in Step 1</h2>
 * The mean of the newest {@code sentiment.current.top-n} scored headlines. Article age is
 * ignored entirely — a headline from an hour ago counts exactly as much as one from six days
 * ago. That is a deliberate simplification for Step 1; the time-weighted cumulative score
 * arrives in Step 2 and will replace only the arithmetic in this class.
 *
 * <p>N defaults to 5 rather than 1. A single-article score is extremely volatile: one headline
 * flips the whole badge, and with roughly one prediction in four wrong, a keyword's displayed
 * sentiment would visibly flicker between page loads. Averaging the newest few is far steadier
 * while still reflecting only recent news.
 *
 * <h2>Unscored articles</h2>
 * Items with a {@code null} score are skipped, not treated as neutral. Conflating "not scored"
 * with "neutral" would drag every average toward zero for reasons unrelated to the news. A
 * keyword whose newest N items are all unscored reports {@link SentimentDto#noData()} rather
 * than a fabricated reading — see {@link SentimentBackfillService} for filling in history.
 */
@Service
public class CurrentSentimentService {

    private static final Logger log = LogManager.getLogger(CurrentSentimentService.class);

    private final CompanyNewsRepository repository;
    private final SentimentScorer scorer;

    /** How many of the newest scored headlines contribute to the current sentiment. */
    private final int topN;

    public CurrentSentimentService(CompanyNewsRepository repository,
                                   SentimentScorer scorer,
                                   @Value("${sentiment.current.top-n:5}") int topN) {
        this.repository = repository;
        this.scorer     = scorer;
        this.topN       = topN;
    }

    /**
     * Returns the current sentiment for a single keyword, reading the row from the database.
     *
     * @param keyword company symbol, sector name, or macro term
     * @return the sentiment, or {@link SentimentDto#noData()} if the keyword has no scored news
     */
    @Transactional(readOnly = true)
    public SentimentDto getForKeyword(String keyword) {
        Optional<CompanyNews> record = repository.findByKeyword(keyword);
        return record.map(this::computeFrom).orElseGet(SentimentDto::noData);
    }

    /**
     * Returns current sentiment for many keywords using a single database query.
     *
     * <p>This is the endpoint behind the watchlist and index / sector company tables. Every
     * requested keyword appears in the result, including ones with no row at all — the caller
     * receives {@link SentimentDto#noData()} for those rather than having to reason about
     * absent map keys.
     *
     * @param keywords the keywords to look up
     * @return an insertion-ordered map of keyword to sentiment, one entry per requested keyword
     */
    @Transactional(readOnly = true)
    public Map<String, SentimentDto> getForKeywords(List<String> keywords) {
        Map<String, SentimentDto> result = new LinkedHashMap<>();
        if (keywords == null || keywords.isEmpty()) return result;

        // Seed with NO_DATA so keywords without a row still appear in the response.
        for (String keyword : keywords) {
            result.put(keyword, SentimentDto.noData());
        }

        for (CompanyNews record : repository.findByKeywordIn(keywords)) {
            result.put(record.getKeyword(), computeFrom(record));
        }

        return result;
    }

    /**
     * Computes current sentiment from an already-loaded record, without touching the database.
     *
     * <p>Used by {@link CompanyNewsService}, which has the row in hand already — the company
     * detail page therefore gets its sentiment without a second query or a second HTTP call.
     *
     * @param record the news record; may be {@code null}
     * @return the sentiment, never {@code null}
     */
    public SentimentDto computeFrom(CompanyNews record) {
        if (record == null || record.getNews() == null || record.getNews().isEmpty()) {
            return SentimentDto.noData();
        }

        // The stored list is maintained newest-first by NewsWorker, which sorts on every save.
        // "Top N" therefore means the first N entries that carry a score.
        List<NewsItem> news = record.getNews();

        double sum = 0.0;
        int count = 0;

        for (NewsItem item : news) {
            Double score = item.getSentimentScore();
            if (score == null) continue;

            sum += score;
            count++;
            if (count == topN) break;
        }

        if (count == 0) {
            log.debug("No scored articles for keyword={} ({} stored) — reporting NO_DATA",
                      record.getKeyword(), news.size());
            return SentimentDto.noData();
        }

        double mean = sum / count;
        return new SentimentDto(round2(mean), scorer.toLabel(mean), count);
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
