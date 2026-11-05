package com.companynews.newsscheduler.dto;

/**
 * One row of a Top Positives or Top Negatives board on the Extremes page.
 *
 * @param rank         1-based position within its board
 * @param symbol       NSE ticker
 * @param companyName  display name, or {@code null} when {@code company_master} has no row for the
 *                     symbol — the UI falls back to the ticker, so a gap costs a nicer label rather
 *                     than a missing entry
 * @param score        mean sentiment for the day or period, on the -5.0..+5.0 scale
 * @param label        {@code POSITIVE} / {@code NEGATIVE} / {@code NEUTRAL}
 * @param articleCount how many scored articles the score rests on.
 *                     <p>The most important number on the row after the score itself. There is no
 *                     minimum article count, so a company whose whole day is one dramatic headline
 *                     ranks directly against one carrying twenty — and because single-article
 *                     scores cluster at the extremes of the scale, the lone headline usually wins.
 *                     Without this count visible the reader cannot tell those two cases apart, and
 *                     the board would read as a measurement rather than as what it is
 */
public record ExtremeEntryDto(int rank,
                              String symbol,
                              String companyName,
                              Double score,
                              String label,
                              int articleCount) {
}
