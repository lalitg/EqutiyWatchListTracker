package com.companynews.newsscheduler.dto;

/**
 * Wrapper that carries both the NewsItem (to be saved)
 * and the seqId (used for duplicate detection in memory only).
 *
 * WHY a separate wrapper:
 * NewsItem no longer has seqId (it was removed from the DB schema).
 * But we still need the seqId from NSE to check the sliding window.
 * This wrapper keeps them together while travelling through the system.
 */
public class NseAnnouncement {

    private final NewsItem newsItem;  // the actual news — will be saved to DB
    private final Long seqId;         // used only for window check — never saved to DB

    public NseAnnouncement(NewsItem newsItem, Long seqId) {
        this.newsItem = newsItem;
        this.seqId = seqId;
    }

    public NewsItem getNewsItem() { return newsItem; }
    public Long getSeqId() { return seqId; }
}
