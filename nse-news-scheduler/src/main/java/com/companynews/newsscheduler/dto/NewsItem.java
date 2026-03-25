package com.companynews.newsscheduler.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Represents a single news article or corporate announcement.
 *
 * <p>This DTO is used both as:
 * <ul>
 *   <li>The element type of the {@code news} JSONB array stored in the {@code company_news} table.</li>
 *   <li>An in-memory transfer object carrying data through the fetch → dedup → save pipeline.</li>
 * </ul>
 *
 * <p>The {@code symbol} field is intentionally annotated with {@link JsonIgnore} so it is
 * excluded from all JSON serialization (DB storage and API responses). It is used only
 * in-memory to group NSE announcements by company symbol during the scheduling pipeline.
 * The {@code keyword} column in {@code company_news} already identifies the company.
 */
public class NewsItem {

    /** Publication date of the article (NSE or RSS format string). */
    private String date;

    /** Headline or subject text of the article or announcement. */
    private String summary;

    /** URL linking to the full article or NSE announcement file. */
    private String link;

    /**
     * Company symbol (e.g., {@code INFY}, {@code RELIANCE}).
     * Used only in-memory to route NSE announcements to the correct keyword bucket.
     * Never serialized to JSON — see {@link JsonIgnore} annotations below.
     */
    @JsonIgnore
    private String symbol;

    /** Default no-arg constructor required by Jackson for deserialization. */
    public NewsItem() {}

    /**
     * Constructs a {@code NewsItem} with the three storable fields.
     *
     * @param date    publication date string (e.g., {@code "12-Mar-2026 17:11:14"} or RFC-822)
     * @param summary headline or subject text
     * @param link    URL to the full article or NSE attachment file
     */
    public NewsItem(String date, String summary, String link) {
        this.date    = date;
        this.summary = summary;
        this.link    = link;
    }

    /**
     * Returns the publication date string of this news item.
     *
     * @return date string in NSE or RSS format
     */
    public String getDate() { return date; }

    /**
     * Sets the publication date string of this news item.
     *
     * @param date date string in NSE or RSS format
     */
    public void setDate(String date) { this.date = date; }

    /**
     * Returns the headline or subject text of this news item.
     *
     * @return summary text
     */
    public String getSummary() { return summary; }

    /**
     * Sets the headline or subject text of this news item.
     *
     * @param summary headline or subject text
     */
    public void setSummary(String summary) { this.summary = summary; }

    /**
     * Returns the URL linking to the full article or NSE attachment.
     *
     * @return article or attachment URL
     */
    public String getLink() { return link; }

    /**
     * Sets the URL linking to the full article or NSE attachment.
     *
     * @param link article or attachment URL
     */
    public void setLink(String link) { this.link = link; }

    /**
     * Returns the in-memory company symbol. Never included in JSON output.
     *
     * @return company symbol (e.g., {@code INFY})
     */
    @JsonIgnore
    public String getSymbol() { return symbol; }

    /**
     * Sets the in-memory company symbol used for routing during the NSE pipeline.
     *
     * @param symbol company symbol (e.g., {@code INFY})
     */
    public void setSymbol(String symbol) { this.symbol = symbol; }
}
