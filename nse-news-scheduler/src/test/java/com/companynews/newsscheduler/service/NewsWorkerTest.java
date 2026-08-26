package com.companynews.newsscheduler.service;

import com.companynews.newsscheduler.client.SentimentModelClient;
import com.companynews.newsscheduler.dto.NewsItem;
import com.companynews.newsscheduler.model.CompanyNews;
import com.companynews.newsscheduler.repository.CompanyNewsRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NewsWorkerTest {

    @Mock
    private CompanyNewsRepository repository;

    @Mock
    private NewsStore newsStore;

    // Use real SimilarityChecker — it has no dependencies and pure deterministic logic
    @Spy
    private SimilarityChecker similarityChecker = new SimilarityChecker();

    private NewsWorker newsWorker;

    @BeforeEach
    void setUp() {
        NewsImportanceClassifier classifier = new NewsImportanceClassifier();
        classifier.init();   // compile phrases from important-keywords.txt (on the test classpath)

        // Sentiment scoring is wired in but deliberately inert here: this suite is about
        // deduplication and persistence, and loading a 219 MB model would make it slow and
        // dependent on an artefact that is not committed. The scorer is constructed with a
        // disabled model client, so score() is a no-op and items keep null sentiment fields —
        // exactly the behaviour these tests already assert on.
        SentimentModelClient disabledModel = new SentimentModelClient(
                false, "models", 64, 1, 1, false, false);
        SentimentScorer sentimentScorer = new SentimentScorer(disabledModel, 1.5, -1.5);

        newsWorker = new NewsWorker(repository, similarityChecker, newsStore, classifier,
                                    sentimentScorer, new SimpleMeterRegistry());
    }

    // ── Helper ─────────────────────────────────────────────────────────────

    private NewsItem item(String link, String summary) {
        NewsItem i = new NewsItem("Mon, 01 Jan 2026 10:00:00 GMT", summary, link);
        return i;
    }

    // ── New keyword (no existing row) ──────────────────────────────────────

    @Test
    void saves_new_items_when_no_existing_row() {
        when(repository.findByKeyword("INFY")).thenReturn(Optional.empty());

        newsWorker.saveNews("INFY", List.of(item("https://a.com/1", "Infosys reports strong Q3")), true);

        ArgumentCaptor<CompanyNews> captor = ArgumentCaptor.forClass(CompanyNews.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getNews()).hasSize(1);
        assertThat(captor.getValue().getKeyword()).isEqualTo("INFY");
    }

    // ── Exact URL duplicate in DB ──────────────────────────────────────────

    @Test
    void skips_item_whose_url_is_already_in_db() {
        NewsItem existing = item("https://a.com/1", "Old headline");
        CompanyNews record = new CompanyNews();
        record.setKeyword("INFY");
        record.setNews(List.of(existing));
        when(repository.findByKeyword("INFY")).thenReturn(Optional.of(record));

        // Submit the same URL again — should be skipped
        newsWorker.saveNews("INFY", List.of(item("https://a.com/1", "Same URL different text")), true);

        // Nothing new added → save should NOT be called
        verify(repository, never()).save(any());
    }

    // ── Exact URL duplicate within the same batch ──────────────────────────

    @Test
    void skips_duplicate_urls_within_incoming_batch() {
        when(repository.findByKeyword("INFY")).thenReturn(Optional.empty());

        // Same URL submitted twice in one batch
        newsWorker.saveNews("INFY", List.of(
            item("https://a.com/1", "Article A"),
            item("https://a.com/1", "Article A again")
        ), true);

        ArgumentCaptor<CompanyNews> captor = ArgumentCaptor.forClass(CompanyNews.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getNews()).hasSize(1); // only one saved
    }

    // ── Similarity duplicate ───────────────────────────────────────────────

    @Test
    void skips_near_duplicate_headline() {
        NewsItem existing = item("https://a.com/1", "Infosys Q3 results beat analyst estimates");
        CompanyNews record = new CompanyNews();
        record.setKeyword("INFY");
        record.setNews(List.of(existing));
        when(repository.findByKeyword("INFY")).thenReturn(Optional.of(record));

        // Near-duplicate headline (different URL — so URL check passes, but similarity check fires)
        newsWorker.saveNews("INFY",
            List.of(item("https://b.com/2", "Infosys Q3 results beat street estimates")), true);

        verify(repository, never()).save(any());
    }

    // ── All deduplicated items are saved (no per-save limit — cleanup scheduler handles retention) ──

    @Test
    void saves_all_deduplicated_items_without_limit() {
        when(repository.findByKeyword("INFY")).thenReturn(Optional.empty());

        List<NewsItem> sixItems = List.of(
            item("https://a.com/1", "Article one about markets"),
            item("https://a.com/2", "Article two on banking sector"),
            item("https://a.com/3", "Article three about RBI policy"),
            item("https://a.com/4", "Article four on Nifty index"),
            item("https://a.com/5", "Article five on crude oil"),
            item("https://a.com/6", "Article six on rupee depreciation")
        );

        newsWorker.saveNews("INFY", sixItems, true);

        ArgumentCaptor<CompanyNews> captor = ArgumentCaptor.forClass(CompanyNews.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getNews()).hasSize(6);
    }

    // ── Empty input ────────────────────────────────────────────────────────

    @Test
    void does_nothing_when_input_list_is_empty() {
        newsWorker.saveNews("INFY", List.of(), true);
        verifyNoInteractions(repository);
    }

    @Test
    void does_nothing_when_input_list_is_null() {
        newsWorker.saveNews("INFY", null, true);
        verifyNoInteractions(repository);
    }

    // ── Items with null link are skipped ───────────────────────────────────

    @Test
    void skips_items_with_null_link() {
        when(repository.findByKeyword("INFY")).thenReturn(Optional.empty());

        NewsItem noLink = new NewsItem("Mon, 01 Jan 2026 10:00:00 GMT", "Some news", null);
        newsWorker.saveNews("INFY", List.of(noLink), true);

        verify(repository, never()).save(any());
    }
}
