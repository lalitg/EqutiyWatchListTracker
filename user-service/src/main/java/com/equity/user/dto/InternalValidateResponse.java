package com.equity.user.dto;

import com.equity.user.enums.InvestorCategory;
import com.equity.user.enums.UserStatus;
import com.equity.user.enums.UserType;

/**
 * Response body for POST /api/v1/internal/users/validate.
 *
 * Returned to auth-service so it can:
 *   1. Verify the password: BCryptPasswordEncoder.matches(plainPassword, passwordHash).
 *   2. Check account status: reject BLOCKED/DELETED accounts before issuing JWT.
 *   3. Embed userId, username, userType, investorCategory into JWT claims.
 *
 * passwordHash is intentionally included here (internal only) because
 * auth-service needs it to verify the credential.  This field must NEVER
 * appear in any public-facing response — hence the separate DTO from UserResponse.
 */
public class InternalValidateResponse {

    private Long userId;
    private String username;
    private String passwordHash;
    private UserType userType;
    private UserStatus status;
    private InvestorCategory investorCategory;

    public InternalValidateResponse(Long userId, String username, String passwordHash,
                                    UserType userType, UserStatus status, InvestorCategory investorCategory) {
        this.userId           = userId;
        this.username         = username;
        this.passwordHash     = passwordHash;
        this.userType         = userType;
        this.status           = status;
        this.investorCategory = investorCategory;
    }

    // ─── Getters ───────────────────────────────────────────────────────────

    public Long getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public UserType getUserType() { return userType; }
    public UserStatus getStatus() { return status; }
    public InvestorCategory getInvestorCategory() { return investorCategory; }
}
