package com.equity.sebi.model;

import java.util.List;

public record BuybackPocResponse(
        String fetchedAt,
        int totalRows,
        int matchedToNse,
        List<BuybackMatchResult> matches
) {}
