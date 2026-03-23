package com.companynews.newsscheduler.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service that detects near-duplicate news headlines using Jaccard similarity.
 *
 * <p>Two headlines are considered duplicates if the overlap of their meaningful words
 * (after stop-word removal) meets or exceeds a configurable threshold (default: 60%).
 *
 * <p>Jaccard similarity formula:
 * <pre>
 *   similarity = |intersection(A, B)| / |union(A, B)|
 * </pre>
 *
 * <p>Example — near-duplicate (similarity = 5/7 ≈ 0.71 → duplicate at 0.6 threshold):
 * <pre>
 *   A: "Infosys Q3 results beat analyst estimates"
 *   B: "Infosys Q3 results beat street estimates"
 *   Intersection: {infosys, q3, results, beat, estimates} = 5
 *   Union: {infosys, q3, results, beat, analyst, estimates, street} = 7
 * </pre>
 *
 * <p>Example — distinct article (similarity = 2/8 = 0.25 → not duplicate):
 * <pre>
 *   A: "Infosys Q3 results beat estimates"
 *   B: "Infosys Q3 earnings exceed expectations"
 *   Intersection: {infosys, q3} = 2
 *   Union: {infosys, q3, results, beat, estimates, earnings, exceed, expectations} = 8
 * </pre>
 */
@Service
public class SimilarityChecker {

    private static final Logger log = LogManager.getLogger(SimilarityChecker.class);

    /** Jaccard similarity threshold above which two headlines are considered duplicates. */
    private static final double THRESHOLD = 0.6;

    /**
     * Common English stop words that are excluded from tokenization.
     *
     * <p>Declared as a {@code static final} field to avoid re-allocating the set on every
     * call to {@link #tokenize(String)}. These words carry no discriminating meaning
     * and would inflate similarity scores if included.
     */
    private static final Set<String> STOP_WORDS = Set.of(
        "the", "a", "an", "and", "or", "but", "in", "on", "at",
        "to", "for", "of", "with", "by", "from", "is", "are",
        "was", "were", "has", "have", "had", "its", "their",
        "this", "that", "as", "it", "be", "been", "about"
    );

    /**
     * Returns {@code true} if {@code newSummary} is too similar to any headline in
     * {@code existingSummaries}, based on Jaccard similarity of word sets.
     *
     * <p>Returns {@code false} immediately (not a duplicate) if:
     * <ul>
     *   <li>{@code newSummary} is {@code null}.</li>
     *   <li>{@code existingSummaries} is {@code null} or empty.</li>
     *   <li>{@code newSummary} tokenizes to an empty set (all stop words or punctuation).</li>
     * </ul>
     *
     * @param newSummary        the candidate headline to check
     * @param existingSummaries the list of already-stored headlines to compare against
     * @return {@code true} if the candidate is a near-duplicate of any existing headline;
     *         {@code false} otherwise
     */
    public boolean isDuplicate(String newSummary, List<String> existingSummaries) {
        if (newSummary == null || existingSummaries == null || existingSummaries.isEmpty()) {
            return false;
        }

        Set<String> newWords = tokenize(newSummary);
        if (newWords.isEmpty()) return false;

        for (String existing : existingSummaries) {
            if (existing == null) continue;
            double similarity = jaccardSimilarity(newWords, tokenize(existing));
            if (similarity >= THRESHOLD) {
                log.debug("Near-duplicate detected (similarity={} >= threshold={}): [{}]",
                        String.format("%.2f", similarity), THRESHOLD, newSummary);
                return true;
            }
        }
        return false;
    }

    /**
     * Computes the Jaccard similarity between two word sets.
     *
     * <p>Jaccard similarity = |intersection| / |union|. Returns 0.0 if either set is empty.
     *
     * @param a the first word set
     * @param b the second word set
     * @return a value between 0.0 (completely different) and 1.0 (identical)
     */
    private double jaccardSimilarity(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;

        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);

        Set<String> union = new HashSet<>(a);
        union.addAll(b);

        return (double) intersection.size() / union.size();
    }

    /**
     * Converts a headline string into a set of meaningful lowercase words.
     *
     * <p>Processing steps:
     * <ol>
     *   <li>Lowercase the entire string.</li>
     *   <li>Strip all non-alphanumeric, non-whitespace characters (punctuation removal).</li>
     *   <li>Split on whitespace.</li>
     *   <li>Remove empty tokens and any token present in {@link #STOP_WORDS}.</li>
     * </ol>
     *
     * @param text the headline string to tokenize; must not be {@code null}
     * @return a set of meaningful lowercase tokens; may be empty if all words are stop words
     */
    private Set<String> tokenize(String text) {
        Set<String> words  = new HashSet<>();
        String[] tokens = text.toLowerCase()
                              .replaceAll("[^a-z0-9\\s]", "")
                              .split("\\s+");
        for (String token : tokens) {
            if (!token.isEmpty() && !STOP_WORDS.contains(token)) {
                words.add(token);
            }
        }
        return words;
    }
}
