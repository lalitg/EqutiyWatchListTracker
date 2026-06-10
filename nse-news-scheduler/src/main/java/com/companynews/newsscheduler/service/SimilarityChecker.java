package com.companynews.newsscheduler.service;

import com.companynews.newsscheduler.dto.NewsItem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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

    /**
     * Threshold used when the new article and an existing article were published on the same day.
     * Lower because same-day articles about the same event use different vocabulary across outlets.
     */
    private static final double SAME_DAY_THRESHOLD = 0.27;

    /**
     * Threshold used when the articles are from different days.
     * Higher to avoid blocking genuinely different stories that share common financial vocabulary.
     */
    private static final double CROSS_DAY_THRESHOLD = 0.6;

    /** NSE date format: {@code "12-Mar-2026 17:11:14"}. Thread-safe. */
    private static final DateTimeFormatter NSE_FORMAT =
        DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss", Locale.ENGLISH);

    /** Google RSS date format: {@code "Thu, 12 Mar 2026 10:00:00 GMT"}. Thread-safe. */
    private static final DateTimeFormatter RSS_FORMAT =
        DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);

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
     * Returns {@code true} if {@code newItem} is too similar to any article in
     * {@code existingItems}, using a time-aware dual-threshold strategy:
     *
     * <ul>
     *   <li><b>Same-day articles</b> (published on the same calendar day in IST) use
     *       {@link #SAME_DAY_THRESHOLD} (0.27) — lower because the same news event covered
     *       by multiple outlets on the same day naturally uses different vocabulary.</li>
     *   <li><b>Cross-day articles</b> use {@link #CROSS_DAY_THRESHOLD} (0.6) — higher to
     *       avoid blocking genuinely different stories that share common financial vocabulary.</li>
     * </ul>
     *
     * <p>Returns {@code false} immediately (not a duplicate) if:
     * <ul>
     *   <li>{@code newItem} or its summary is {@code null}.</li>
     *   <li>{@code existingItems} is {@code null} or empty.</li>
     *   <li>The summary tokenizes to an empty set (all stop words or punctuation).</li>
     * </ul>
     *
     * @param newItem       the candidate article to check
     * @param existingItems the list of already-stored articles to compare against
     * @return {@code true} if the candidate is a near-duplicate of any existing article;
     *         {@code false} otherwise
     */
    public boolean isDuplicate(NewsItem newItem, List<NewsItem> existingItems) {
        if (newItem == null || newItem.getSummary() == null
                || existingItems == null || existingItems.isEmpty()) {
            return false;
        }

        String newSummary = newItem.getSummary();
        Set<String> newWords = tokenize(newSummary);
        if (newWords.isEmpty()) return false;

        LocalDate newDate = parseToLocalDate(newItem.getDate());

        for (NewsItem existing : existingItems) {
            if (existing.getSummary() == null) continue;

            double similarity = jaccardSimilarity(newWords, tokenize(existing.getSummary()));
            boolean sameDay   = isSameDay(newDate, parseToLocalDate(existing.getDate()));
            double threshold  = sameDay ? SAME_DAY_THRESHOLD : CROSS_DAY_THRESHOLD;

            if (similarity >= threshold) {
                log.debug("Near-duplicate detected (similarity={} >= threshold={}, same-day={}): [{}]",
                        String.format("%.2f", similarity), threshold, sameDay, newSummary);
                return true;
            }
        }
        return false;
    }

    /**
     * Parses a date string to a {@link LocalDate} in IST (Asia/Kolkata).
     * Tries NSE format first, then Google RSS format.
     * Returns {@code null} if the string is null or neither format matches —
     * callers treat a null date as cross-day (uses the stricter threshold).
     *
     * @param dateStr the date string to parse
     * @return the parsed {@link LocalDate}, or {@code null} if unparseable
     */
    private LocalDate parseToLocalDate(String dateStr) {
        if (dateStr == null) return null;
        try {
            return LocalDateTime.parse(dateStr, NSE_FORMAT).toLocalDate();
        } catch (Exception ignored) {}
        try {
            return ZonedDateTime.parse(dateStr, RSS_FORMAT)
                    .withZoneSameInstant(ZoneId.of("Asia/Kolkata"))
                    .toLocalDate();
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Returns {@code true} if both dates are non-null and represent the same calendar day.
     *
     * @param d1 first date
     * @param d2 second date
     * @return {@code true} if same day, {@code false} if either is null or they differ
     */
    private boolean isSameDay(LocalDate d1, LocalDate d2) {
        return d1 != null && d1.equals(d2);
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
