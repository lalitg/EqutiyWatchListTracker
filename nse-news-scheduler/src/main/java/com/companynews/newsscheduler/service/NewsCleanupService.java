package com.companynews.newsscheduler.service;

import com.companynews.newsscheduler.dto.NewsItem;
import com.companynews.newsscheduler.model.CompanyNews;
import com.companynews.newsscheduler.repository.CompanyNewsRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Applies the news retention policy to the {@code company_news} table.
 *
 * <p>Two policies, selected per row by keyword type:
 * <ul>
 *   <li><b>Sector / macro keywords</b> — unchanged: keep all articles within the last
 *       {@code news.retention.window-hours} hours, with a {@code news.retention.min-count}
 *       floor of newest older items so the frontend always has content.</li>
 *   <li><b>Company symbols</b> — dual retention for the two-tab UI: keep an article if it is
 *       within the {@code news.retention.company.latest-window-days} window (Latest tab) OR it
 *       is flagged {@code category="important"} and within the
 *       {@code news.retention.company.important-window-days} window (Important tab). The same
 *       min-count floor is applied to the Latest portion for quiet companies.</li>
 * </ul>
 *
 * <p>Articles with unparseable or missing dates are treated as fresh and never removed.
 */
@Service
public class NewsCleanupService {

    private static final Logger log = LogManager.getLogger(NewsCleanupService.class);

    private final CompanyNewsRepository repository;
    private final NewsStore newsStore;
    private final KeywordLoader keywordLoader;

    @Value("${news.retention.window-hours:24}")
    private int retentionWindowHours;

    @Value("${news.retention.min-count:15}")
    private int minCount;

    @Value("${news.retention.company.latest-window-days:7}")
    private int companyLatestWindowDays;

    @Value("${news.retention.company.important-window-days:90}")
    private int companyImportantWindowDays;

    public NewsCleanupService(CompanyNewsRepository repository,
                              NewsStore newsStore,
                              KeywordLoader keywordLoader) {
        this.repository    = repository;
        this.newsStore     = newsStore;
        this.keywordLoader = keywordLoader;
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
    @Transactional
    public void cleanup() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
        ZonedDateTime sectorCutoff           = now.minusHours(retentionWindowHours);
        ZonedDateTime companyLatestCutoff     = now.minusDays(companyLatestWindowDays);
        ZonedDateTime companyImportantCutoff  = now.minusDays(companyImportantWindowDays);

        // Loaded once per run (hourly) — cheap in-memory membership check per row afterwards.
        Set<String> companySymbols = keywordLoader.loadCompanySymbols();

        List<CompanyNews> allRecords = repository.findAll();
        int rowsUpdated = 0;

        for (CompanyNews record : allRecords) {
            List<NewsItem> news = record.getNews();
            if (news == null || news.isEmpty()) continue;

            boolean isCompany = companySymbols.contains(record.getKeyword());
            List<NewsItem> retained = isCompany
                ? retainForCompany(news, companyLatestCutoff, companyImportantCutoff)
                : retainForSector(news, sectorCutoff);

            // retained is always a subset in original order — equal size means nothing was removed.
            if (retained.size() == news.size()) continue;

            record.setNews(retained);
            record.setLastUpdated(LocalDateTime.now());
            repository.save(record);
            newsStore.put(record.getKeyword(), retained);
            rowsUpdated++;

            log.debug("Cleanup: keyword={} isCompany={} kept={}/{}",
                record.getKeyword(), isCompany, retained.size(), news.size());
        }

        log.info("Cleanup completed — {}/{} keyword rows updated", rowsUpdated, allRecords.size());
    }

    /**
     * Sector/macro retention (unchanged behavior): keep items within the hours-based window;
     * if fewer than {@code minCount} are fresh, pad with the newest older items as a floor.
     *
     * @return the retained subset in original (newest-first) order
     */
    private List<NewsItem> retainForSector(List<NewsItem> news, ZonedDateTime cutoff) {
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

        if (old.isEmpty()) return news;                 // nothing outside window — unchanged
        if (fresh.size() >= minCount) return fresh;     // enough fresh — drop all old

        // Pad with the newest old items (list is already newest-first from NewsWorker)
        List<NewsItem> retained = new ArrayList<>(fresh);
        int needed = minCount - fresh.size();
        retained.addAll(old.subList(0, Math.min(needed, old.size())));
        return retained;
    }

    /**
     * Company retention for the two-tab UI. Keeps an item if it is within the Latest window OR
     * it is flagged important and within the Important window. Additionally force-keeps the
     * newest {@code minCount} items overall so the read-path floor always has content for a
     * quiet company's Latest tab (mirrors {@code CompanyNewsService}'s floor).
     *
     * @return the retained subset in original (newest-first) order
     */
    private List<NewsItem> retainForCompany(List<NewsItem> news,
                                            ZonedDateTime latestCutoff,
                                            ZonedDateTime importantCutoff) {
        int floor = Math.min(minCount, news.size());
        List<NewsItem> retained = new ArrayList<>();

        for (int i = 0; i < news.size(); i++) {
            NewsItem item = news.get(i);

            // Floor: always keep the newest minCount items regardless of age/category.
            if (i < floor) {
                retained.add(item);
                continue;
            }

            ZonedDateTime date = NewsDateParser.parse(item.getDate());
            boolean withinLatest    = date == null || date.isAfter(latestCutoff);
            boolean withinImportant = date == null || date.isAfter(importantCutoff);
            boolean isImportant =
                NewsImportanceClassifier.CATEGORY_IMPORTANT.equals(item.getCategory());

            if (withinLatest || (isImportant && withinImportant)) {
                retained.add(item);
            }
        }

        return retained;
    }

}
