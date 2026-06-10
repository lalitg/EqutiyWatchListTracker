package com.equity.fundamentals.util;

import java.time.LocalDate;

/**
 * Converts a LocalDate (the last day of a quarter) to an Indian fiscal quarter label.
 *
 * Indian fiscal year runs April–March.
 * FY2025 = April 2024 – March 2025.
 *
 * Quarter mapping:
 *   Apr–Jun  → Q1 of the FY that ENDS next March  (e.g. 2024-06-30 → Q1FY25)
 *   Jul–Sep  → Q2  (e.g. 2024-09-30 → Q2FY25)
 *   Oct–Dec  → Q3  (e.g. 2024-12-31 → Q3FY25)
 *   Jan–Mar  → Q4  (e.g. 2025-03-31 → Q4FY25)
 *
 * Examples:
 *   2024-06-30 → Q1FY25  (FY25 starts April 2024)
 *   2024-09-30 → Q2FY25
 *   2024-12-31 → Q3FY25
 *   2025-03-31 → Q4FY25
 *   2023-06-30 → Q1FY24
 */
public class QuarterUtil {

    private QuarterUtil() {}

    /**
     * Returns the Indian fiscal quarter label for the given period-end date.
     *
     * @param periodEnd last day of the quarter (e.g. LocalDate.of(2024, 9, 30))
     * @return quarter label e.g. "Q2FY25"
     */
    public static String toQuarterLabel(LocalDate periodEnd) {
        int month = periodEnd.getMonthValue();
        int year  = periodEnd.getYear();

        // FY suffix = last 2 digits of the year the FY ends in (March year)
        if (month >= 4 && month <= 6)  return "Q1FY" + ((year + 1) % 100);
        if (month >= 7 && month <= 9)  return "Q2FY" + ((year + 1) % 100);
        if (month >= 10)               return "Q3FY" + ((year + 1) % 100);
        return "Q4FY" + (year % 100);  // Jan–Mar: FY ends this year
    }
}
