package com.equity.user.enums;

/**
 * How many years of investing experience a user has.
 *
 * Each constant carries a numeric `score` used by InvestorCategoryService to
 * compute the user's investor category.  More years = higher score.
 *
 *   ZERO_TO_FIVE    → score 1  (beginner experience)
 *   FIVE_TO_TEN     → score 2
 *   TEN_TO_FIFTEEN  → score 3
 *   FIFTEEN_PLUS    → score 4  (most experienced)
 *
 * Stored as VARCHAR(20) in the DB column `investment_years` (enum name as string).
 * Combined with InvestmentAmount score → total score → InvestorCategory.
 */
public enum InvestmentYears {

    ZERO_TO_FIVE(1),
    FIVE_TO_TEN(2),
    TEN_TO_FIFTEEN(3),
    FIFTEEN_PLUS(4);

    private final int score;

    InvestmentYears(int score) {
        this.score = score;
    }

    public int getScore() {
        return score;
    }
}
