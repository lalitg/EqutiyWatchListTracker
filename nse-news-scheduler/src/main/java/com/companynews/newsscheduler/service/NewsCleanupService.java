package com.companynews.newsscheduler.service;

import com.companynews.newsscheduler.dto.NewsItem;
import com.companynews.newsscheduler.model.CompanyNews;
import com.companynews.newsscheduler.repository.CompanyNewsRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies the news retention policy to the {@code company_news} table.
 *
 * <p>Retention rule: for each keyword row, keep all articles published within the last
 * {@code news.retention.window-hours} hours. If fewer than {@code news.retention.min-count}
 * articles fall within that window, retain the newest older articles as a floor so the
 * frontend always has enough items to display.
 *
 * <p>Articles with unparseable or missing dates are treated as fresh and never removed.
 */
@Service
public class NewsCleanupService {

    private static final Logger log = LogManager.getLogger(NewsCleanupService.class);

    private final CompanyNewsRepository repository;
    private final NewsStore newsStore;

    @Value("${news.retention.window-hours:24}")
    private int retentionWindowHours;

    @Value("${news.retention.min-count:15}")
    private int minCount;

    public NewsCleanupService(CompanyNewsRepository repository, NewsStore newsStore) {
        this.repository = repository;
        this.newsStore  = newsStore;
    }

    /**
     * Loads every keyword row, applies the retention policy, and saves back any rows that changed.
     *
     * <p>For each row:
     * <ol>
     *   <li>Partition the JSONB news array into <em>fresh</em> (within the window) and
     *       <em>old</em> (outside the window).</li>
     *   <li>If fresh count &ge; {@code min-count}: discard old items entirely.</li>
     *   <li>If fresh count &lt; {@code min-count}: pad with the newest old items until
     *       the floor is reached.</li>
     *   <li>Skip the DB write if nothing changed (no old items existed).</li>
     * </ol>
     */
    @CacheEvict(value = "mergedNews", allEntries = true)
    @Transactional
    public void cleanup() {
        ZonedDateTime cutoff = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"))
            .minusHours(retentionWindowHours);

        List<CompanyNews> allRecords = repository.findAll();
        int rowsUpdated = 0;

        for (CompanyNews record : allRecords) {
            List<NewsItem> news = record.getNews();
            if (news == null || news.isEmpty()) continue;

            List<NewsItem> fresh = new ArrayList<>();
            List<NewsItem> old   = new ArrayList<>();

            for (NewsItem item : news) {
                ZonedDateTime itemDate = NewsDateParser.parse(item.getDate());
                // Items with unparseable dates are kept (treated as fresh)
                if (itemDate == null || itemDate.isAfter(cutoff)) {
                    fresh.add(item);
                } else {
                    old.add(item);
                }
            }

            // Nothing outside the window — no changes needed
            if (old.isEmpty()) continue;

            List<NewsItem> retained;
            if (fresh.size() >= minCount) {
                retained = fresh;
            } else {
                // Pad with the newest old items (list is already newest-first from NewsWorker)
                retained = new ArrayList<>(fresh);
                int needed = minCount - fresh.size();
                retained.addAll(old.subList(0, Math.min(needed, old.size())));
            }

            record.setNews(retained);
            record.setLastUpdated(LocalDateTime.now());
            repository.save(record);
            newsStore.put(record.getKeyword(), retained);
            rowsUpdated++;

            log.debug("Cleanup: keyword={} total={} (fresh={} floor_kept={})",
                record.getKeyword(), retained.size(), fresh.size(),
                retained.size() - fresh.size());
        }

        log.info("Cleanup completed — {}/{} keyword rows updated", rowsUpdated, allRecords.size());
    }

}
