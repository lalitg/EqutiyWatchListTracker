package com.companynews.newsscheduler.dto;

/**
 * One row of a company's Sentiments tab: a period (or the latest article) and its reading.
 *
 * <p>Returned as an ordered list by {@code GET /api/news/sentiment/windows?key=} and as the
 * {@code sentimentWindows} field of {@code GET /api/news?key=}. The order is the declaration order
 * of {@link SentimentWindow}, so the frontend renders the list as received without sorting.
 *
 * @param window       machine-readable key, e.g. {@code WEEK_2}. Present so the UI can key off a
 *                     stable identifier rather than the display string
 * @param label        human-readable name, e.g. {@code "Last 2 weeks"}
 * @param score        the reading on the -5.0..+5.0 scale — a plain average for the period rows,
 *                     the article's own score for {@link SentimentWindow#LATEST} — or {@code null}
 *                     when nothing qualified
 * @param sentiment    {@code POSITIVE} / {@code NEGATIVE} / {@code NEUTRAL}, or {@code NO_DATA}
 *                     when {@code score} is {@code null}
 * @param articleCount how many scored headlines contributed; always {@code 1} for
 *                     {@link SentimentWindow#LATEST}.
 *                     <p>This is not decoration. A plain average says nothing about how much
 *                     evidence sits behind it, so a period holding one article and a period holding
 *                     forty are indistinguishable from the score alone. Serving the count alongside
 *                     is what lets the UI show a single-article reading as the weak signal it is —
 *                     and it is also how {@code NO_DATA} (count {@code 0}) stays visibly different
 *                     from a genuine neutral reading of {@code 0.0}
 * @param publishedAt  publication instant in epoch milliseconds. Set <b>only</b> for
 *                     {@link SentimentWindow#LATEST}, where the reading rests on one article and
 *                     its age is the reader's only way to judge how much weight to give it.
 *                     {@code null} for every period row, where an average spans many dates and a
 *                     single timestamp would be meaningless
 */
public record SentimentWindowDto(String window,
                                 String label,
                                 Double score,
                                 String sentiment,
                                 int articleCount,
                                 Long publishedAt) {

    /**
     * Builds the empty row for a window that contains no scored articles.
     *
     * <p>Rows are never omitted from the response when they are empty: the tab shows a fixed set of
     * rows, and a missing one would be read as a rendering fault rather than as an absence of news.
     * Most companies genuinely have nothing published today, so this is the common case and not an
     * error path.
     *
     * @param window the window this row describes
     * @return a row carrying {@code NO_DATA} and a zero count
     */
    public static SentimentWindowDto noData(SentimentWindow window) {
        return new SentimentWindowDto(window.name(), window.label(), null,
                                      SentimentDto.LABEL_NO_DATA, 0, null);
    }
}
