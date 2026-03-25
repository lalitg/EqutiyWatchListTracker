package com.companynews.newsscheduler.service;

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

    @Test
    void identical_headlines_are_duplicates() {
        String headline = "Infosys Q3 results beat analyst estimates";
        assertThat(checker.isDuplicate(headline, List.of(headline))).isTrue();
    }

    @Test
    void highly_similar_headlines_are_duplicates() {
        // Intersection: {infosys, q3, results, beat, estimates} = 5
        // Union: {infosys, q3, results, beat, analyst, estimates, street} = 7
        // Similarity = 5/7 = 0.71 → above threshold 0.6 → duplicate
        String existing = "Infosys Q3 results beat analyst estimates";
        String incoming = "Infosys Q3 results beat street estimates";
        assertThat(checker.isDuplicate(incoming, List.of(existing))).isTrue();
    }

    @Test
    void distinct_headlines_are_not_duplicates() {
        // Intersection: {infosys, q3} = 2
        // Union: {infosys, q3, results, beat, estimates, earnings, exceed, expectations} = 8
        // Similarity = 2/8 = 0.25 → below threshold → not duplicate
        String existing = "Infosys Q3 results beat estimates";
        String incoming = "Infosys Q3 earnings exceed expectations";
        assertThat(checker.isDuplicate(incoming, List.of(existing))).isFalse();
    }

    @Test
    void completely_different_headlines_are_not_duplicates() {
        String existing = "RBI raises repo rate by 25 basis points";
        String incoming = "Wipro wins cloud contract with European bank";
        assertThat(checker.isDuplicate(incoming, List.of(existing))).isFalse();
    }

    @Test
    void null_new_summary_returns_false() {
        assertThat(checker.isDuplicate(null, List.of("some headline"))).isFalse();
    }

    @Test
    void null_existing_list_returns_false() {
        assertThat(checker.isDuplicate("some headline", null)).isFalse();
    }

    @Test
    void empty_existing_list_returns_false() {
        assertThat(checker.isDuplicate("some headline", List.of())).isFalse();
    }

    @Test
    void matches_against_any_headline_in_list() {
        // First headline is completely different; second is a near-duplicate
        String incoming = "Infosys Q3 results beat street estimates";
        List<String> existing = List.of(
            "RBI raises repo rate",
            "Infosys Q3 results beat analyst estimates"  // near-duplicate
        );
        assertThat(checker.isDuplicate(incoming, existing)).isTrue();
    }

    @Test
    void stop_words_are_ignored_in_comparison() {
        // Without stop-word removal, "the" and "a" would inflate similarity incorrectly.
        // With stop-word removal, these two distinct headlines should NOT match.
        String existing = "The RBI has raised the repo rate";
        String incoming = "A major flood hits the coastal region";
        assertThat(checker.isDuplicate(incoming, List.of(existing))).isFalse();
    }
}
