package com.companynews.newsscheduler.service;

import com.companynews.newsscheduler.client.SentimentModelClient;
import com.companynews.newsscheduler.dto.NewsItem;
import com.companynews.newsscheduler.model.CompanyNews;
import com.companynews.newsscheduler.repository.CompanyNewsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the dual (company) vs. legacy (sector) retention branching in
 * {@link NewsCleanupService}.
 */
@ExtendWith(MockitoExtension.class)
class NewsCleanupServiceTest {

    private static final DateTimeFormatter NSE_FORMAT =
        DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss", Locale.ENGLISH);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Mock private CompanyNewsRepository repository;
    @Mock private NewsStore newsStore;
    @Mock private KeywordLoader keywordLoader;

    private NewsCleanupService service;

    @BeforeEach
    void setUp() {
        // A real CurrentSentimentService rather than a mock: cleanup calls refresh() on every row
        // it rewrites, and these tests assert on the saved record. A mock would silently accept
        // that call and leave the denormalised columns untouched, hiding a regression where
        // cleanup drops articles without updating the sentiment derived from them. Its own
        // repository is never used — refresh() reads the in-memory record only.
        CurrentSentimentService sentimentService = new CurrentSentimentService(
                mock(CompanyNewsRepository.class),
                new SentimentScorer(mock(SentimentModelClient.class), 1.5, -1.5));

        // These window values are the ones the retention tests below were written against; they
        // are set explicitly rather than inherited from application.properties so that changing
        // the production policy cannot silently change what these tests assert.
        service = new NewsCleanupService(repository, newsStore, keywordLoader, sentimentService);
        ReflectionTestUtils.setField(service, "retentionWindowHours", 24);
        ReflectionTestUtils.setField(service, "minCount", 15);
        ReflectionTestUtils.setField(service, "companyLatestWindowDays", 7);
        ReflectionTestUtils.setField(service, "companyImportantWindowDays", 90);
    }

    /** Builds a news item dated {@code daysAgo} days before now, with an optional category. */
    private NewsItem itemDaysAgo(String url, String summary, int daysAgo, String category) {
        String date = ZonedDateTime.now(IST).minusDays(daysAgo).format(NSE_FORMAT);
        NewsItem i = new NewsItem(date, summary, url);
        i.setCategory(category);
        return i;
    }

    private CompanyNews row(String keyword, List<NewsItem> news) {
        CompanyNews r = new CompanyNews();
        r.setKeyword(keyword);
        r.setNews(new ArrayList<>(news));
        return r;
    }

    @Test
    void company_keeps_recent_and_important_drops_old_normal() {
        // 20 recent normal items (well over the min-count floor of 15), so the floor does not
        // interfere with the age/category assertions on the older items below.
        List<NewsItem> news = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            news.add(itemDaysAgo("https://a.com/recent" + i, "recent " + i, 1, null));
        }
        NewsItem oldNormal    = itemDaysAgo("https://a.com/oldnormal", "old normal", 10, null);
        NewsItem oldImportant = itemDaysAgo("https://a.com/oldimp", "buyback news", 40,
            NewsImportanceClassifier.CATEGORY_IMPORTANT);
        NewsItem ancientImportant = itemDaysAgo("https://a.com/ancientimp", "old dividend", 100,
            NewsImportanceClassifier.CATEGORY_IMPORTANT);
        news.add(oldNormal);
        news.add(oldImportant);
        news.add(ancientImportant);

        when(keywordLoader.loadCompanySymbols()).thenReturn(Set.of("INFY"));
        when(repository.findAll()).thenReturn(List.of(row("INFY", news)));

        service.cleanup();

        ArgumentCaptor<CompanyNews> captor = ArgumentCaptor.forClass(CompanyNews.class);
        verify(repository).save(captor.capture());
        List<String> keptUrls = captor.getValue().getNews().stream().map(NewsItem::getLink).toList();

        assertThat(keptUrls).contains("https://a.com/oldimp");        // important within 90d — kept
        assertThat(keptUrls).doesNotContain("https://a.com/oldnormal");  // normal at 10d — dropped
        assertThat(keptUrls).doesNotContain("https://a.com/ancientimp"); // important at 100d — dropped
        assertThat(keptUrls).contains("https://a.com/recent0");       // recent — kept
    }

    @Test
    void company_floor_keeps_newest_items_for_quiet_company() {
        // Only 3 items, all older than the 7-day latest window and none important.
        // The min-count floor must still keep the newest items so the Latest tab isn't empty.
        List<NewsItem> news = List.of(
            itemDaysAgo("https://a.com/1", "old 1", 30, null),
            itemDaysAgo("https://a.com/2", "old 2", 40, null),
            itemDaysAgo("https://a.com/3", "old 3", 50, null)
        );

        when(keywordLoader.loadCompanySymbols()).thenReturn(Set.of("INFY"));
        when(repository.findAll()).thenReturn(List.of(row("INFY", news)));

        service.cleanup();

        // All 3 fit under the floor of 15, so no article is removed.
        ArgumentCaptor<CompanyNews> captor = ArgumentCaptor.forClass(CompanyNews.class);
        verify(repository, atMost(1)).save(captor.capture());
        if (!captor.getAllValues().isEmpty()) {
            assertThat(captor.getValue().getNews()).hasSize(3);
        }
    }

    @Test
    void quiet_company_with_up_to_date_columns_is_not_rewritten() {
        // The cost-critical property. Cleanup now recomputes sentiment on EVERY company row, not
        // just ones whose article list shrank, because a quarter average ages out on its own while
        // a company sits silent. That is only affordable if a row where nothing moved is left
        // alone — otherwise every JSONB blob in the table would be rewritten hourly for nothing.
        List<NewsItem> news = List.of(
            itemDaysAgo("https://a.com/1", "old 1", 30, null),
            itemDaysAgo("https://a.com/2", "old 2", 40, null)
        );

        CompanyNews record = row("INFY", news);
        // Pre-set the columns to exactly what a refresh would produce for unscored articles, i.e.
        // this row has already been through cleanup once.
        record.setLatestLabel("NO_DATA");
        record.setQuarterLabel("NO_DATA");
        record.setQuarterCount(0);

        when(keywordLoader.loadCompanySymbols()).thenReturn(Set.of("INFY"));
        when(repository.findAll()).thenReturn(List.of(record));

        service.cleanup();

        verify(repository, never()).save(any());
    }

    @Test
    void quiet_company_with_uninitialised_columns_is_written_once() {
        // The other half of the same rule: a row that predates the denormalised columns must be
        // written once to populate them. Without this the batch endpoint behind the watchlist and
        // the index tables would report NO_DATA for that company indefinitely, since nothing else
        // touches a silent row.
        List<NewsItem> news = List.of(itemDaysAgo("https://a.com/1", "old 1", 30, null));

        when(keywordLoader.loadCompanySymbols()).thenReturn(Set.of("INFY"));
        when(repository.findAll()).thenReturn(List.of(row("INFY", news)));

        service.cleanup();

        ArgumentCaptor<CompanyNews> captor = ArgumentCaptor.forClass(CompanyNews.class);
        verify(repository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getLatestLabel()).isEqualTo("NO_DATA");
        assertThat(captor.getValue().getNews()).hasSize(1);   // no article was dropped
    }

    @Test
    void sector_row_uses_legacy_24h_retention_unaffected_by_company_windows() {
        // A sector keyword (NOT in the company set) with items 2 days old — under the company
        // 7-day window but well past the sector 24h window. Legacy retention must drop them
        // (subject only to the min-count floor), proving the company windows don't leak in.
        List<NewsItem> news = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            news.add(itemDaysAgo("https://s.com/fresh" + i, "fresh " + i, 0, null));
        }
        news.add(itemDaysAgo("https://s.com/stale", "2 days old", 2, null));

        when(keywordLoader.loadCompanySymbols()).thenReturn(Set.of("INFY"));  // "Banking" not a company
        when(repository.findAll()).thenReturn(List.of(row("Banking", news)));

        service.cleanup();

        ArgumentCaptor<CompanyNews> captor = ArgumentCaptor.forClass(CompanyNews.class);
        verify(repository).save(captor.capture());
        List<String> keptUrls = captor.getValue().getNews().stream().map(NewsItem::getLink).toList();

        // 20 fresh items exceed the floor, so the 2-day-old item is dropped by the 24h window.
        assertThat(keptUrls).doesNotContain("https://s.com/stale");
        assertThat(keptUrls).hasSize(20);
    }
}