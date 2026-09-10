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
     * Importance category of this article.
     *
     * <p>{@code null} for normal news; {@code "important"} when the headline matched one of
     * the corporate-action phrases in {@code important-keywords.txt} (dividend, buyback,
     * stock split, bonus issue, merger, etc.). Set at save time by
     * {@link com.companynews.newsscheduler.service.NewsImportanceClassifier}, but only for
     * company-symbol keywords — never for sectors or macro keywords.
     *
     * <p>Stored inside the {@code news} JSONB array. Rows written before this field existed
     * simply deserialize with {@code category == null}, so no migration is required.
     */
    private String category;

    /**
     * Company symbol (e.g., {@code INFY}, {@code RELIANCE}).
     * Used only in-memory to route NSE announcements to the correct keyword bucket.
     * Never serialized to JSON — see {@link JsonIgnore} annotations below.
     */
    @JsonIgnore
    private String symbol;

    /**
     * Sentiment score for this headline on a symmetric scale of -5.0 (very negative)
     * to +5.0 (very positive), with 0.0 meaning neutral.
     *
     * <p>Derived from the model's probability distribution rather than its argmax label:
     * {@code (p_positive - p_negative) * 5.0}. Using the difference keeps the score
     * continuous — a hedged headline yields a small magnitude, a decisive one a large
     * magnitude — which matters because these values are later averaged.
     *
     * <p>Assigned once, at save time, by
     * {@link com.companynews.newsscheduler.service.SentimentScorer} — the same hook where
     * {@link com.companynews.newsscheduler.service.NewsImportanceClassifier} runs. Scoring
     * applies to company keywords only; sectors and macro keywords are left {@code null}.
     *
     * <p>{@code null} for rows written before this field existed, and whenever scoring is
     * disabled or the model is unavailable. Like {@code category}, this lives inside the
     * {@code news} JSONB array, so adding it required no database migration — older rows
     * simply deserialize with {@code null}.
     */
    private Double sentimentScore;

    /**
     * Human-readable band derived from {@link #sentimentScore} using the configured
     * thresholds: {@code "POSITIVE"}, {@code "NEGATIVE"}, or {@code "NEUTRAL"}.
     *
     * <p>Stored alongside the raw score so the frontend does not have to duplicate the
     * threshold logic, and so a threshold change is visible as a data change rather than
     * silently altering how historical articles are displayed.
     *
     * <p>{@code null} whenever {@link #sentimentScore} is {@code null}.
     */
    private String sentimentLabel;

    /**
     * Publication instant in epoch milliseconds, normalised from {@link #date} at save time.
     *
     * <p>{@link #date} is a display string in one of two formats (NSE or RFC-822 RSS). Every
     * time-based operation — retention, sorting, and the sentiment time windows — needs it as an
     * instant, and re-deriving it means running {@link com.companynews.newsscheduler.service.NewsDateParser}
     * over the whole stored array on each pass. Storing the parsed value turns those filters into
     * integer comparisons.
     *
     * <p>Set once by {@link com.companynews.newsscheduler.service.NewsWorker} when an item is
     * accepted, alongside category and sentiment. {@code null} for articles stored before this
     * field existed and for the small number whose date string matches neither format; callers
     * must fall back to parsing {@link #date} rather than assuming a value is present.
     * {@link com.companynews.newsscheduler.service.SentimentBackfillService} fills it in for
     * existing rows.
     *
     * <p>Lives inside the {@code news} JSONB array, so adding it required no database migration —
     * older rows simply deserialize with {@code null}, exactly as {@code category} and
     * {@code sentimentScore} did before it.
     */
    private Long publishedAt;

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
     * Returns the importance category of this news item.
     *
     * @return {@code "important"} if flagged, or {@code null} for normal news
     */
    public String getCategory() { return category; }

    /**
     * Sets the importance category of this news item.
     *
     * @param category {@code "important"} or {@code null}
     */
    public void setCategory(String category) { this.category = category; }

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

    /**
     * Returns this headline's sentiment score on the -5.0 to +5.0 scale.
     *
     * @return the score, or {@code null} if this item was never scored
     */
    public Double getSentimentScore() { return sentimentScore; }

    /**
     * Sets this headline's sentiment score.
     *
     * @param sentimentScore score on the -5.0 to +5.0 scale, or {@code null}
     */
    public void setSentimentScore(Double sentimentScore) { this.sentimentScore = sentimentScore; }

    /**
     * Returns the sentiment band for this headline.
     *
     * @return {@code "POSITIVE"}, {@code "NEGATIVE"}, {@code "NEUTRAL"}, or {@code null}
     */
    public String getSentimentLabel() { return sentimentLabel; }

    /**
     * Sets the sentiment band for this headline.
     *
     * @param sentimentLabel {@code "POSITIVE"}, {@code "NEGATIVE"}, {@code "NEUTRAL"}, or {@code null}
     */
    public void setSentimentLabel(String sentimentLabel) { this.sentimentLabel = sentimentLabel; }

    /**
     * Returns the publication instant in epoch milliseconds.
     *
     * @return epoch millis, or {@code null} if this item predates the field or its date string
     *         could not be parsed
     */
    public Long getPublishedAt() { return publishedAt; }

    /**
     * Sets the publication instant in epoch milliseconds.
     *
     * @param publishedAt epoch millis, or {@code null} if the date string was unparseable
     */
    public void setPublishedAt(Long publishedAt) { this.publishedAt = publishedAt; }
}
