package com.companynews.newsscheduler.dto;

/**
 * The rows shown on a company's Sentiments tab.
 *
 * <h2>Two different kinds of row</h2>
 * {@link #LATEST} is <b>one article</b> — the most recent scored headline, whatever its age. Every
 * other entry is an <b>average over a period</b>. They answer different questions and are not
 * comparable: Latest says "what did the last piece of news say", the periods say "what has the
 * news been like lately".
 *
 * <p>Latest also sits outside the nesting the others follow, and it is the only row guaranteed to
 * have a value whenever a company has any scored news at all. That is what makes the rest free to
 * be honestly empty.
 *
 * <h2>The periods are nested</h2>
 * The quarter contains the month, which contains the fortnight, and so on. Neighbouring rows will
 * often read similarly — that means little has changed, not that a figure is broken. The article
 * count served with each score is what makes the difference legible.
 *
 * <h2>Why TODAY is a calendar day, not "the newest few articles"</h2>
 * This row used to be {@code CURRENT}: the mean of the newest five scored headlines regardless of
 * age. That is a useful number but it is emphatically not "today" — for a quiet company those five
 * headlines can span a month, and labelling them Today would have been false.
 *
 * <p>So Today means the calendar day, midnight to midnight in {@code Asia/Kolkata} — the timezone
 * the market and the schedulers already run on — exactly like {@link #YESTERDAY}. The consequence
 * is that Today is empty for most companies on most days, which is correct rather than broken, and
 * is affordable precisely because {@link #LATEST} always carries a reading.
 */
public enum SentimentWindow {

    /**
     * The single most recent scored headline. Not an average, and not age-limited.
     *
     * <p>Carries its publication date so the reader can see how old the reading is; a three-month-old
     * headline should be visibly three months old rather than presented as though it were current.
     */
    LATEST("Latest", 0),

    /** The current calendar day in {@code Asia/Kolkata}, midnight to now. */
    TODAY("Today", 0),

    /** The previous calendar day in {@code Asia/Kolkata}, midnight to midnight. */
    YESTERDAY("Yesterday", 0),

    /** Rolling 7-day lookback. */
    WEEK_1("Last 1 week", 7),

    /** Rolling 14-day lookback. */
    WEEK_2("Last 2 weeks", 14),

    /** Rolling 30-day lookback. */
    MONTH_1("Last 1 month", 30),

    /**
     * Rolling 90-day lookback — the longest window, and the reason company news is retained for a
     * quarter. A quarter of retention is what makes this figure mean anything; under the previous
     * seven-day policy it would have been a seven-day average wearing a longer label.
     *
     * <p>This window is also served in the company tables alongside Latest, so its length is the
     * single definition both the tab and those tables read.
     */
    QUARTER_1("Last 1 quarter", 90);

    private final String label;
    private final int days;

    SentimentWindow(String label, int days) {
        this.label = label;
        this.days  = days;
    }

    /** @return human-readable name for the UI, e.g. {@code "Last 2 weeks"} */
    public String label() {
        return label;
    }

    /**
     * @return the lookback length in days, or {@code 0} for {@link #LATEST}, {@link #TODAY} and
     *         {@link #YESTERDAY}, whose ranges are not simple lookbacks
     */
    public int days() {
        return days;
    }

    /** @return {@code true} if this row is a plain rolling lookback of {@link #days()} days */
    public boolean isRolling() {
        return days > 0;
    }
}
