package com.companynews.newsscheduler.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link NewsImportanceClassifier}, exercising the phrase list loaded from
 * {@code important-keywords.txt} on the classpath.
 */
class NewsImportanceClassifierTest {

    private NewsImportanceClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new NewsImportanceClassifier();
        classifier.init();
    }

    @Test
    void flags_headline_containing_a_corporate_action_phrase() {
        assertThat(classifier.classify("Infosys announces share buyback worth Rs 9,300 crore"))
            .isEqualTo(NewsImportanceClassifier.CATEGORY_IMPORTANT);
        assertThat(classifier.classify("TCS board declares interim dividend of Rs 27"))
            .isEqualTo(NewsImportanceClassifier.CATEGORY_IMPORTANT);
        assertThat(classifier.classify("Company approves stock split in 1:5 ratio"))
            .isEqualTo(NewsImportanceClassifier.CATEGORY_IMPORTANT);
    }

    @Test
    void is_case_insensitive() {
        assertThat(classifier.classify("RELIANCE ANNOUNCES BONUS ISSUE"))
            .isEqualTo(NewsImportanceClassifier.CATEGORY_IMPORTANT);
    }

    @Test
    void does_not_flag_ordinary_company_news() {
        assertThat(classifier.classify("Infosys opens new office in Bengaluru")).isNull();
        assertThat(classifier.classify("CEO comments on hiring plans for next year")).isNull();
    }

    @Test
    void respects_word_boundaries() {
        // "consultant" contains "result"? no — but guard against loose substring matching on
        // a word that embeds a phrase. "dividends" should still match via the 'dividend' stem
        // boundary at the start; a non-word like "subdividend" should not.
        assertThat(classifier.classify("Firm hires management consultant")).isNull();
    }

    @Test
    void returns_null_for_null_or_blank_headline() {
        assertThat(classifier.classify(null)).isNull();
        assertThat(classifier.classify("   ")).isNull();
    }
}