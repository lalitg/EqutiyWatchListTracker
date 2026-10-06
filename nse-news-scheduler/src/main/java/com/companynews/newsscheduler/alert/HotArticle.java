package com.companynews.newsscheduler.alert;

/**
 * One article that crossed the alert threshold, ready to be mailed.
 *
 * @param keyword     the company symbol it was filed under
 * @param link        the original article — the thing a reader actually needs
 * @param headline    the headline text
 * @param score       sentiment on the −5…+5 scale
 * @param publishedAt when the article was published, epoch millis
 */
public record HotArticle(String keyword,
                         String link,
                         String headline,
                         double score,
                         long publishedAt) {

    /** Good news or bad news — the only split the email makes. */
    public boolean isPositive() {
        return score > 0;
    }
}
