package com.equity.user.enums;

/**
 * Computed investor tier based on the combined score of
 * InvestmentYears + InvestmentAmount.
 *
 * Score thresholds (from the LLD score matrix):
 *   score ≤ 3  → BEGINNER      (new to investing, small capital)
 *   score ≤ 5  → INTERMEDIATE  (some experience or moderate capital)
 *   score ≤ 7  → ADVANCED      (seasoned investor)
 *   score > 7  → PRO           (expert with large capital)
 *
 * Max possible score = 4 (years) + 5 (amount) = 9.
 *
 * This value is embedded in the JWT by auth-service so that watchlist-service
 * can gate PRO/ADVANCED features without calling user-service on every request.
 *
 * Stored as VARCHAR(15) in the DB column `investor_category`.
 * Recomputed automatically whenever the user updates their investor profile.
 */
public enum InvestorCategory {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED,
    PRO
}
