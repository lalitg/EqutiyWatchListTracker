package com.equity.sebi.model;

public record BuybackMatchResult(
        String nseSymbol,
        String nseCompanyName,
        String sebiCompanyName,
        MatchTier matchTier,
        String buybackDate,
        String sebiUrl
) {}
