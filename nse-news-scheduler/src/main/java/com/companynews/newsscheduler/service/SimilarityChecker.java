package com.companynews.newsscheduler.service;

import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class SimilarityChecker {

    // Two headlines are duplicates if their Jaccard similarity meets or exceeds this threshold
    private static final double THRESHOLD = 0.6;

    /**
     * Returns true if newSummary is too similar to any headline in existingSummaries.
     *
     * Uses Jaccard Similarity on word sets:
     *   similarity = |intersection| / |union|
     *
     * Example — near-duplicate (similarity = 5/7 = 0.71 → duplicate):
     *   A: "Infosys Q3 results beat analyst estimates"
     *   B: "Infosys Q3 results beat street estimates"
     *   Intersection: {infosys, q3, results, beat, estimates} = 5
     *   Union: {infosys, q3, results, beat, analyst, estimates, street} = 7
     *
     * Example — distinct article (similarity = 2/8 = 0.25 → not duplicate):
     *   A: "Infosys Q3 results beat estimates"
     *   B: "Infosys Q3 earnings exceed expectations"
     *   Intersection: {infosys, q3} = 2
     *   Union: {infosys, q3, results, beat, estimates, earnings, exceed, expectations} = 8
     */
    public boolean isDuplicate(String newSummary, List<String> existingSummaries) {
        if (newSummary == null || existingSummaries == null || existingSummaries.isEmpty()) {
            return false;
        }

        Set<String> newWords = tokenize(newSummary);
        if (newWords.isEmpty()) return false;

        for (String existing : existingSummaries) {
            if (existing == null) continue;
            if (jaccardSimilarity(newWords, tokenize(existing)) >= THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    private double jaccardSimilarity(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;

        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);

        Set<String> union = new HashSet<>(a);
        union.addAll(b);

        return (double) intersection.size() / union.size();
    }

    /**
     * Converts a headline to a set of meaningful lowercase words.
     * Strips punctuation and removes stop words that would inflate similarity scores.
     */
    private Set<String> tokenize(String text) {
        Set<String> stopWords = Set.of(
            "the", "a", "an", "and", "or", "but", "in", "on", "at",
            "to", "for", "of", "with", "by", "from", "is", "are",
            "was", "were", "has", "have", "had", "its", "their",
            "this", "that", "as", "it", "be", "been", "about"
        );

        Set<String> words = new HashSet<>();
        String[] tokens = text.toLowerCase()
                              .replaceAll("[^a-z0-9\\s]", "")
                              .split("\\s+");
        for (String token : tokens) {
            if (!token.isEmpty() && !stopWords.contains(token)) {
                words.add(token);
            }
        }
        return words;
    }
}
