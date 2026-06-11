package com.equity.sebi.model;

public enum MatchTier {
    EXACT,        // normalized names are identical
    SUBSTRING,    // one normalized name contains the other (length ratio >= 0.7)
    TOKEN_JACCARD // token overlap >= 0.6
}
