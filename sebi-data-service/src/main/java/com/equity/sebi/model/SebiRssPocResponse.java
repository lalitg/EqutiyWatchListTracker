package com.equity.sebi.model;

import java.util.List;

public record SebiRssPocResponse(
        String fetchedAt,
        int totalRssItems,
        int matchedToNse,
        List<RssMatchResult> matches
) {}
