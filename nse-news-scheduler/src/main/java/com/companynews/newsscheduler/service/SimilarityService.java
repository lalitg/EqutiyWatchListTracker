package com.companynews.newsscheduler.service;

import org.springframework.stereotype.Service;

@Service
public class SimilarityService {

    // Two headlines are considered duplicates if similarity >= this threshold
    private static final double SIMILARITY_THRESHOLD = 0.6;

    /**
     * Checks if a new headline is too similar to any existing headline.
     * Uses Jaccard Similarity on word sets.
     *
     * WHY Jaccard Similarity:
     * Simple, no external libraries needed, works well for short texts
     * like news headlines.
     *
     * HOW Jaccard works:
     * Split both headlines into words (tokens).
     * Count words that appear in BOTH (intersection).
     * Count words that appear in EITHER (union).
     * Similarity = intersection size / union size
     *
     * Example:
     * Headline A: "Infosys Q3 results beat estimates"
     * Words A: {infosys, q3, results, beat, estimates}
     *
     * Headline B: "Infosys Q3 earnings exceed expectations"
     * Words B: {infosys, q3, earnings, exceed, expectations}
     *
     * Intersection: {infosys, q3} → size 2
     * Union: {infosys, q3, results, beat, estimates, earnings, exceed, expectations} → size 8
     * Similarity: 2/8 = 0.25 → NOT duplicate (below 0.6)
     *
     * Example 2:
     * Headline A: "Infosys Q3 results beat analyst estimates"
     * Headline B: "Infosys Q3 results beat street estimates"
     * Intersection: {infosys, q3, results, beat, estimates} → size 5
     * Union: {infosys, q3, results, beat, analyst, estimates, street} → size 7
     * Similarity: 5/7 = 0.71 → DUPLICATE (above 0.6)
     *
     * @param newSummary the incoming news headline to check
     * @param existingSummaries list of already-saved headlines for this keyword
     * @return true if newSummary is too similar to any existing headline
     */
    public boolean isDuplicate(String newSummary, java.util.List<String> existingSummaries) {
        if (newSummary == null || existingSummaries == null || existingSummaries.isEmpty()) {
            return false;
        }

        // Normalize and tokenize the new headline
        java.util.Set<String> newWords = tokenize(newSummary);
        if (newWords.isEmpty()) return false;

        // Compare against each existing headline
        for (String existing : existingSummaries) {
            if (existing == null) continue;
            java.util.Set<String> existingWords = tokenize(existing);
            double similarity = jaccardSimilarity(newWords, existingWords);
            if (similarity >= SIMILARITY_THRESHOLD) {
                return true;  // Too similar — duplicate
            }
        }
        return false;
    }

    /**
     * Calculates Jaccard similarity between two word sets.
     * Returns a value between 0.0 (completely different) and 1.0 (identical).
     */
    private double jaccardSimilarity(java.util.Set<String> setA,
                                      java.util.Set<String> setB) {
        if (setA.isEmpty() || setB.isEmpty()) return 0.0;

        // Intersection — words in both sets
        java.util.Set<String> intersection = new java.util.HashSet<>(setA);
        intersection.retainAll(setB);  // keeps only elements present in both

        // Union — words in either set
        java.util.Set<String> union = new java.util.HashSet<>(setA);
        union.addAll(setB);  // adds all elements from both

        return (double) intersection.size() / union.size();
    }

    /**
     * Converts a headline string into a set of lowercase words.
     * Removes punctuation and common stop words that add noise.
     *
     * WHY remove stop words:
     * Words like "the", "a", "of", "in" appear in almost every headline.
     * They inflate the similarity score between unrelated headlines.
     * Removing them makes the comparison focus on meaningful words.
     */
    private java.util.Set<String> tokenize(String text) {
        // Stop words — common words that carry no meaning for comparison
        java.util.Set<String> stopWords = java.util.Set.of(
            "the", "a", "an", "and", "or", "but", "in", "on", "at",
            "to", "for", "of", "with", "by", "from", "is", "are",
            "was", "were", "has", "have", "had", "its", "their",
            "this", "that", "as", "it", "be", "been", "about"
        );

        java.util.Set<String> words = new java.util.HashSet<>();

        // Remove punctuation, convert to lowercase, split by spaces
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
