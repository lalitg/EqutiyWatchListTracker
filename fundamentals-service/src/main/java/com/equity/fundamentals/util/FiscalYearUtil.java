package com.equity.fundamentals.util;

import java.time.LocalDate;

/**
 * Converts a LocalDate (last day of the fiscal year) to an Indian fiscal year label.
 *
 * Indian fiscal year runs April–March.
 * The fiscal year is named after the year it ENDS in (March year).
 *
 * Examples:
 *   2024-03-31 → FY2024  (April 2023 – March 2024)
 *   2023-03-31 → FY2023
 *   2025-03-31 → FY2025
 *
 * Note: balance sheet period_end_date from yfinance is always 31-March for annual.
 * For any non-March year-end (rare foreign-listed companies), we use the calendar year.
 */
public class FiscalYearUtil {

    private FiscalYearUtil() {}

    /**
     * Returns the Indian fiscal year label for the given period-end date.
     *
     * @param periodEnd last day of the fiscal year (e.g. LocalDate.of(2024, 3, 31))
     * @return fiscal year label e.g. "FY2024"
     */
    public static String toFiscalYearLabel(LocalDate periodEnd) {
        return "FY" + periodEnd.getYear();
    }
}
