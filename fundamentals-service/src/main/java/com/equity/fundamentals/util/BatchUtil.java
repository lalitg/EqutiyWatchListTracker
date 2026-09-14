package com.equity.fundamentals.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a list into fixed-size chunks — used to bound how many symbols get
 * passed to a single yfinance_wrapper.py invocation. See QuarterlyResultsService /
 * BalanceSheetService / PeSnapshotService for why: one Python process per chunk,
 * not one for the whole global_watchlist (thousands of symbols could mean an
 * unreasonably long single invocation) and not one per symbol (interpreter/import
 * startup cost paid thousands of times).
 */
public final class BatchUtil {

    private BatchUtil() {}

    public static <T> List<List<T>> partition(List<T> items, int chunkSize) {
        List<List<T>> chunks = new ArrayList<>();
        for (int i = 0; i < items.size(); i += chunkSize) {
            chunks.add(items.subList(i, Math.min(i + chunkSize, items.size())));
        }
        return chunks;
    }
}