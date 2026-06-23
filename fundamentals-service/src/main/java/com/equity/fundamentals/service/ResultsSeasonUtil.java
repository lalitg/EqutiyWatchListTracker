package com.equity.fundamentals.service;

import org.springframework.stereotype.Component;

import java.time.MonthDay;

/**
 * Determines whether today falls within an NSE quarterly results season.
 *
 * Indian companies are required by SEBI to publish quarterly results within
 * 45 days of quarter end. This creates 4 "results seasons" per year:
 *
 *   Q1 (Apr–Jun) results season: Jul 15 – Aug 31
 *   Q2 (Jul–Sep) results season: Oct 15 – Nov 30
 *   Q3 (Oct–Dec) results season: Jan 15 – Feb 28
 *   Q4 (Jan–Mar) results season: May 01 – Jun 30
 *
 * The nightly scheduler checks this before fetching — if it is off-season, the
 * nightly fetch is skipped to avoid useless API calls. A weekly Sunday sync
 * still runs year-round to catch amendments or restatements.
 */
@Component
public class ResultsSeasonUtil {

    public boolean isResultsSeason() {
        MonthDay today = MonthDay.now();
        return isBetween(today, MonthDay.of(7,  15), MonthDay.of(8,  31))  // Q1 season
            || isBetween(today, MonthDay.of(10, 15), MonthDay.of(11, 30))  // Q2 season
            || isBetween(today, MonthDay.of(1,  15), MonthDay.of(2,  28))  // Q3 season
            || isBetween(today, MonthDay.of(5,  1),  MonthDay.of(6,  30)); // Q4 season
    }

    private boolean isBetween(MonthDay d, MonthDay start, MonthDay end) {
        return !d.isBefore(start) && !d.isAfter(end);
    }
}
