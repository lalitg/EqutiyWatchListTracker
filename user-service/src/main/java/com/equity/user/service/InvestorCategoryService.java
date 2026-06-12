package com.equity.user.service;

import com.equity.user.enums.InvestmentAmount;
import com.equity.user.enums.InvestmentYears;
import com.equity.user.enums.InvestorCategory;
import org.springframework.stereotype.Service;

/**
 * Computes a user's investor category from the LLD score matrix.
 *
 * Score matrix:
 *   InvestmentYears  : ZERO_TO_FIVE=1, FIVE_TO_TEN=2, TEN_TO_FIFTEEN=3, FIFTEEN_PLUS=4
 *   InvestmentAmount : ZERO_TO_5L=1, FIVE_TO_10L=2, TEN_TO_20L=3, TWENTY_TO_50L=4, FIFTY_PLUS=5
 *
 *   Total score = years.score + amount.score  (range: 2–9)
 *
 *   score ≤ 3  → BEGINNER
 *   score ≤ 5  → INTERMEDIATE
 *   score ≤ 7  → ADVANCED
 *   score > 7  → PRO
 *
 * This is a pure stateless computation — no DB access, no side effects.
 * It is called by UserService every time the investor profile is updated.
 */
@Service
public class InvestorCategoryService {

    /**
     * Computes the investor category from the two profile fields.
     *
     * @param years  how many years the user has been investing (must not be null)
     * @param amount how much capital the user has invested (must not be null)
     * @return the corresponding InvestorCategory enum value
     */
    public InvestorCategory compute(InvestmentYears years, InvestmentAmount amount) {
        int score = years.getScore() + amount.getScore();

        if (score <= 3) return InvestorCategory.BEGINNER;
        if (score <= 5) return InvestorCategory.INTERMEDIATE;
        if (score <= 7) return InvestorCategory.ADVANCED;
        return InvestorCategory.PRO;
    }
}
