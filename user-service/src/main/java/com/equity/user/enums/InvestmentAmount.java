package com.equity.user.enums;

/**
 * Total capital a user has invested (in INR).
 *
 * Each constant carries a numeric `score` used by InvestorCategoryService.
 * More capital = higher score = higher investor category.
 *
 *   ZERO_TO_5L      → score 1  (up to ₹5 lakh)
 *   FIVE_TO_10L     → score 2  (₹5–10 lakh)
 *   TEN_TO_20L      → score 3  (₹10–20 lakh)
 *   TWENTY_TO_50L   → score 4  (₹20–50 lakh)
 *   FIFTY_PLUS      → score 5  (above ₹50 lakh)
 *
 * Stored as VARCHAR(20) in the DB column `investment_amount` (enum name as string).
 */
public enum InvestmentAmount {

    ZERO_TO_5L(1),
    FIVE_TO_10L(2),
    TEN_TO_20L(3),
    TWENTY_TO_50L(4),
    FIFTY_PLUS(5);

    private final int score;

    InvestmentAmount(int score) {
        this.score = score;
    }

    public int getScore() {
        return score;
    }
}
