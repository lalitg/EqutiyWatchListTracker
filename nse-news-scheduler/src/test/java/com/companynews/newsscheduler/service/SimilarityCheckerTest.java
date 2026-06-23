package com.companynews.newsscheduler.service;

import com.companynews.newsscheduler.dto.NewsItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SimilarityCheckerTest {

    private SimilarityChecker checker;

    @BeforeEach
    void setUp() {
        checker = new SimilarityChecker();
    }

    /** Creates a NewsItem with no date (null date → cross-day threshold applies). */
    private NewsItem item(String summary) {
        return new NewsItem(null, summary, null);
    }

    /** Creates a NewsItem with an explicit publication date and summary. */
    private NewsItem item(String date, String summary) {
        return new NewsItem(date, summary, null);
    }

    @Test
    void identical_headlines_are_duplicates() {
        String headline = "Infosys Q3 results beat analyst estimates";
        assertThat(checker.isDuplicate(item(headline), List.of(item(headline)))).isTrue();
    }

    @Test
    void highly_similar_headlines_are_duplicates() {
        // Intersection: {infosys, q3, results, beat, estimates} = 5
        // Union: {infosys, q3, results, beat, analyst, estimates, street} = 7
        // Similarity = 5/7 = 0.71 → above cross-day threshold 0.6 → duplicate
        NewsItem existing = item("Infosys Q3 results beat analyst estimates");
        NewsItem incoming = item("Infosys Q3 results beat street estimates");
        assertThat(checker.isDuplicate(incoming, List.of(existing))).isTrue();
    }

    @Test
    void distinct_headlines_are_not_duplicates() {
        // Intersection: {infosys, q3} = 2
        // Union: {infosys, q3, results, beat, estimates, earnings, exceed, expectations} = 8
        // Similarity = 2/8 = 0.25 → below both thresholds (0.27 same-day, 0.6 cross-day) → not duplicate
        NewsItem existing = item("Infosys Q3 results beat estimates");
        NewsItem incoming = item("Infosys Q3 earnings exceed expectations");
        assertThat(checker.isDuplicate(incoming, List.of(existing))).isFalse();
    }

    @Test
    void completely_different_headlines_are_not_duplicates() {
        NewsItem existing = item("RBI raises repo rate by 25 basis points");
        NewsItem incoming = item("Wipro wins cloud contract with European bank");
        assertThat(checker.isDuplicate(incoming, List.of(existing))).isFalse();
    }

    @Test
    void null_item_returns_false() {
        assertThat(checker.isDuplicate(null, List.of(item("some headline")))).isFalse();
    }

    @Test
    void null_summary_returns_false() {
        assertThat(checker.isDuplicate(item(null), List.of(item("some headline")))).isFalse();
    }

    @Test
    void null_existing_list_returns_false() {
        assertThat(checker.isDuplicate(item("some headline"), null)).isFalse();
    }

    @Test
    void empty_existing_list_returns_false() {
        assertThat(checker.isDuplicate(item("some headline"), List.of())).isFalse();
    }

    @Test
    void matches_against_any_headline_in_list() {
        // First item is completely different; second is a near-duplicate
        NewsItem incoming = item("Infosys Q3 results beat street estimates");
        List<NewsItem> existing = List.of(
            item("RBI raises repo rate"),
            item("Infosys Q3 results beat analyst estimates")  // near-duplicate
        );
        assertThat(checker.isDuplicate(incoming, existing)).isTrue();
    }

    @Test
    void stop_words_are_ignored_in_comparison() {
        // Without stop-word removal, "the" and "a" would inflate similarity incorrectly.
        // With stop-word removal, these two distinct headlines should NOT match.
        NewsItem existing = item("The RBI has raised the repo rate");
        NewsItem incoming = item("A major flood hits the coastal region");
        assertThat(checker.isDuplicate(incoming, List.of(existing))).isFalse();
    }

    @Test
    void same_day_articles_use_lower_threshold() {
        // Same-day threshold = 0.27
        // These two HDFC Bank WFH articles: Jaccard ≈ 5/13 = 0.385 → above 0.27 → duplicate
        NewsItem existing = item("Wed, 20 May 2026 09:00:00 GMT",
                "HDFC Bank announces 2-day WFH policy for businesses and corporate verticals");
        NewsItem incoming = item("Wed, 20 May 2026 10:00:00 GMT",
                "HDFC Bank to allow 2-day WFH for business corporate functions Sources");
        assertThat(checker.isDuplicate(incoming, List.of(existing))).isTrue();
    }

    @Test
    void cross_day_articles_use_higher_threshold() {
        // Cross-day threshold = 0.6
        // Same two articles but published on different days: Jaccard ≈ 0.385 → below 0.6 → NOT duplicate
        NewsItem existing = item("Tue, 19 May 2026 09:00:00 GMT",
                "HDFC Bank announces 2-day WFH policy for businesses and corporate verticals");
        NewsItem incoming = item("Wed, 20 May 2026 10:00:00 GMT",
                "HDFC Bank to allow 2-day WFH for business corporate functions Sources");
        assertThat(checker.isDuplicate(incoming, List.of(existing))).isFalse();
    }
}
