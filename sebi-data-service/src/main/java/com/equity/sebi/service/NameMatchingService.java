package com.equity.sebi.service;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Three-tier company name matcher.
 *
 * Tier 1 — Exact: both sides normalize to identical string.
 * Tier 2 — Substring: shorter normalized name is contained in longer,
 *           with length ratio >= 0.70 to prevent "HDFC" matching "HDFC Bank".
 * Tier 3 — Token Jaccard: token overlap >= 0.60 with at least 2 tokens each side.
 */
@Component
public class NameMatchingService {

    // Legal-form words stripped during normalization
    private static final Pattern LEGAL_SUFFIX = Pattern.compile(
            "\\b(limited|ltd\\.?|private|pvt\\.?|public|llp|llc|co\\.?|company|incorporated|inc\\.?)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9\\s]");
    private static final Pattern MULTI_SPACE       = Pattern.compile("\\s+");
    private static final Pattern THE_PREFIX        = Pattern.compile("^the\\s+");

    private static final double SUBSTRING_LENGTH_RATIO = 0.70;
    private static final double JACCARD_THRESHOLD       = 0.60;
    private static final int    MIN_TOKEN_COUNT         = 2;
    private static final int    MIN_NORMALIZED_LENGTH   = 4;

    public String normalize(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String s = raw.toLowerCase().trim();
        s = LEGAL_SUFFIX.matcher(s).replaceAll(" ");
        s = THE_PREFIX.matcher(s).replaceAll("");
        s = NON_ALPHANUMERIC.matcher(s).replaceAll(" ");
        s = MULTI_SPACE.matcher(s).replaceAll(" ").trim();
        return s;
    }

    /**
     * Attempts to match candidateName against every entry in the provided index.
     * Returns the first (highest-tier) match found, or empty.
     */
    public Optional<String> findBestMatch(String candidateName, Set<String> normalizedKeys) {
        String norm = normalize(candidateName);
        if (norm.length() < MIN_NORMALIZED_LENGTH) return Optional.empty();

        // Tier 1 — exact
        if (normalizedKeys.contains(norm)) return Optional.of(norm);

        // Tier 2 — substring with length guard
        for (String key : normalizedKeys) {
            if (key.length() < MIN_NORMALIZED_LENGTH) continue;
            String longer  = norm.length() >= key.length() ? norm : key;
            String shorter = norm.length() < key.length()  ? norm : key;
            if (shorter.length() < MIN_NORMALIZED_LENGTH) continue;
            double ratio = (double) shorter.length() / longer.length();
            if (ratio >= SUBSTRING_LENGTH_RATIO && longer.contains(shorter)) {
                return Optional.of(key);
            }
        }

        // Tier 3 — token Jaccard
        Set<String> candTokens = tokenSet(norm);
        if (candTokens.size() < MIN_TOKEN_COUNT) return Optional.empty();

        String bestKey   = null;
        double bestScore = 0.0;
        for (String key : normalizedKeys) {
            Set<String> keyTokens = tokenSet(key);
            if (keyTokens.size() < MIN_TOKEN_COUNT) continue;
            double score = jaccard(candTokens, keyTokens);
            if (score > bestScore) {
                bestScore = score;
                bestKey   = key;
            }
        }
        if (bestScore >= JACCARD_THRESHOLD) return Optional.of(bestKey);

        return Optional.empty();
    }

    private Set<String> tokenSet(String normalized) {
        Set<String> tokens = new HashSet<>(Arrays.asList(normalized.split("\\s+")));
        tokens.removeIf(t -> t.length() < 2);
        return tokens;
    }

    private double jaccard(Set<String> a, Set<String> b) {
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }
}
