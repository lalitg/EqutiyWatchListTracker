package com.equity.sebi.model;

import java.util.List;

public record RssMatchResult(
        String nseSymbol,
        String nseCompanyName,
        String extractedName,
        MatchTier matchTier,
        String eventType,
        String pubDate,
        String sebiUrl,
        String fullTitle
) {}
