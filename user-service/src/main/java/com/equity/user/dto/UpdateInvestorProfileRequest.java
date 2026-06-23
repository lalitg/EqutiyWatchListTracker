package com.equity.user.dto;

import com.equity.user.enums.InvestmentAmount;
import com.equity.user.enums.InvestmentYears;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for PUT /api/v1/users/me/investor-profile.
 *
 * Both fields are required — when the user updates their investor profile,
 * they must supply both values so the category can be (re-)computed from
 * the full score matrix.
 *
 * After a successful update:
 *   1. investmentYears and investmentAmount are saved on the User entity.
 *   2. InvestorCategoryService recomputes the investor_category.
 *   3. All three columns are flushed to the DB in one transaction.
 *
 * Note: the updated investorCategory is returned in the UserResponse so the
 * frontend can show the user their new tier immediately.
 */
public class UpdateInvestorProfileRequest {

    @NotNull(message = "Investment years is required")
    private InvestmentYears investmentYears;

    @NotNull(message = "Investment amount is required")
    private InvestmentAmount investmentAmount;

    // ─── Getters & Setters ─────────────────────────────────────────────────

    public InvestmentYears getInvestmentYears() { return investmentYears; }
    public void setInvestmentYears(InvestmentYears investmentYears) { this.investmentYears = investmentYears; }

    public InvestmentAmount getInvestmentAmount() { return investmentAmount; }
    public void setInvestmentAmount(InvestmentAmount investmentAmount) { this.investmentAmount = investmentAmount; }
}
