package com.companynews.newsscheduler.dto;

/**
 * Wrapper DTO that carries both a {@link NewsItem} and the NSE sequence ID together
 * through the NSE fetch → dedup → save pipeline.
 *
 * <p>WHY a separate wrapper instead of putting {@code seqId} on {@link NewsItem}:
 * {@code seqId} is an NSE-internal identifier used only for in-memory duplicate detection
 * via {@link com.companynews.newsscheduler.service.SeqIdWindow}. It is intentionally never
 * stored in the database. Keeping it separate from {@link NewsItem} prevents accidental
 * persistence and makes the intent explicit: {@link NewsItem} is the storable payload,
 * {@code seqId} is the dedup key that lives only in memory.
 */
public class NseAnnouncement {

    /** The actual news content that will be saved to the {@code company_news} table. */
    private final NewsItem newsItem;

    /** NSE sequence ID used only for sliding-window deduplication — never saved to the DB. */
    private final Long seqId;

    /**
     * Constructs an {@code NseAnnouncement} pairing a news item with its NSE sequence ID.
     *
     * @param newsItem the news content to be stored; must not be {@code null}
     * @param seqId    the NSE sequence ID for deduplication; must not be {@code null}
     */
    public NseAnnouncement(NewsItem newsItem, Long seqId) {
        this.newsItem = newsItem;
        this.seqId    = seqId;
    }

    /**
     * Returns the news item carrying storable content (date, summary, link, symbol).
     *
     * @return the wrapped {@link NewsItem}
     */
    public NewsItem getNewsItem() { return newsItem; }

    /**
     * Returns the NSE sequence ID used for deduplication in the sliding window.
     *
     * @return the NSE sequence ID
     */
    public Long getSeqId() { return seqId; }
}
