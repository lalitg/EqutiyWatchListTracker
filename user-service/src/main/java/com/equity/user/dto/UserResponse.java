package com.equity.user.dto;

import com.equity.user.entity.User;
import com.equity.user.enums.InvestmentAmount;
import com.equity.user.enums.InvestmentYears;
import com.equity.user.enums.InvestorCategory;
import com.equity.user.enums.UserStatus;
import com.equity.user.enums.UserType;

import java.time.LocalDateTime;

/**
 * Outbound DTO returned by every endpoint that describes a user.
 *
 * Why a separate DTO instead of returning User directly?
 * - Prevents passwordHash from ever leaking into API responses.
 * - Decouples the API contract from the DB schema — the entity can change
 *   without breaking clients as long as this DTO stays stable.
 * - Lets us rename or add fields independently on each side.
 *
 * Built via the static factory method: UserResponse.from(user).
 */
public class UserResponse {

    private Long id;
    private String username;
    private String name;
    private String email;
    private String phoneNumber;
    private UserType userType;
    private UserStatus status;
    private InvestmentYears investmentYears;
    private InvestmentAmount investmentAmount;
    private InvestorCategory investorCategory;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Convenience factory: maps a User entity → UserResponse.
     * Called in UserService instead of duplicating field assignments everywhere.
     */
    public static UserResponse from(User user) {
        UserResponse r = new UserResponse();
        r.id               = user.getId();
        r.username         = user.getUsername();
        r.name             = user.getName();
        r.email            = user.getEmail();
        r.phoneNumber      = user.getPhoneNumber();
        r.userType         = user.getUserType();
        r.status           = user.getStatus();
        r.investmentYears  = user.getInvestmentYears();
        r.investmentAmount = user.getInvestmentAmount();
        r.investorCategory = user.getInvestorCategory();
        r.createdAt        = user.getCreatedAt();
        r.updatedAt        = user.getUpdatedAt();
        return r;
    }

    // ─── Getters (no setters — this is a read-only response object) ─────────

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhoneNumber() { return phoneNumber; }
    public UserType getUserType() { return userType; }
    public UserStatus getStatus() { return status; }
    public InvestmentYears getInvestmentYears() { return investmentYears; }
    public InvestmentAmount getInvestmentAmount() { return investmentAmount; }
    public InvestorCategory getInvestorCategory() { return investorCategory; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
