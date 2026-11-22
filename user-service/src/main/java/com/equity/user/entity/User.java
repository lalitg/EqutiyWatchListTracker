package com.equity.user.entity;

import com.equity.user.enums.InvestmentAmount;
import com.equity.user.enums.InvestmentYears;
import com.equity.user.enums.InvestorCategory;
import com.equity.user.enums.UserStatus;
import com.equity.user.enums.UserType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * JPA entity that maps to the `users` table.
 *
 * Every registered user has exactly one row here.  The table is owned by
 * Flyway (V1__create_users_table.sql); Hibernate is in validate-mode only.
 *
 * Key design decisions:
 * - password_hash stores only the BCrypt hash; the plain-text password is
 *   never saved anywhere.
 * - investor_category is derived (computed by InvestorCategoryService) —
 *   it is updated atomically with investment_years and investment_amount.
 * - email and phone_number are nullable so a user can register with just a
 *   username and password initially.
 * - Soft-delete: status = DELETED keeps the row for audit / FK integrity.
 */
@Entity
@Table(name = "users")
public class User {

    /** Auto-incremented surrogate PK. Used as JWT `sub` claim. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique login handle chosen at registration.
     * Also embedded in the JWT so downstream services can display the name
     * without hitting user-service.
     */
    @Column(unique = true, nullable = false, length = 50)
    private String username;

    /** Display name (full name). */
    @Column(nullable = false, length = 100)
    private String name;

    /** Optional. Used for account-recovery flows. */
    @Column(unique = true, length = 150)
    private String email;

    /** Optional. Used for OTP / SMS flows in the future. */
    @Column(name = "phone_number", unique = true, length = 15)
    private String phoneNumber;

    /**
     * Whether the email address above has been proved to belong to this user.
     *
     * WHY a Boolean and not a primitive boolean: this column is added to a table that already has
     * rows, so every existing user carries NULL here until they verify. Hibernate cannot read NULL
     * into a primitive, and adding the column as NOT NULL would fail on those same rows. The getter
     * normalises NULL to false, which is the correct reading: never verified.
     */
    @Column(name = "email_verified")
    private Boolean emailVerified;

    /**
     * The single live verification token, or null when there is none outstanding.
     *
     * Unique so two users can never hold the same token. Postgres allows any number of NULLs in a
     * unique index, so users without a pending verification do not collide.
     */
    @Column(name = "email_verification_token", unique = true, length = 64)
    private String emailVerificationToken;

    /** When the token above stops working. Null whenever the token is null. */
    @Column(name = "email_verification_expires_at")
    private LocalDateTime emailVerificationExpiresAt;

    /**
     * BCrypt hash of the user's password.
     * BCrypt always produces exactly 60 characters ($2a$ format).
     * Never logged, never returned in API responses.
     */
    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;

    /**
     * Role: CLIENT (investor) or ADMIN (operator).
     * Stored as the enum name string, e.g. "CLIENT".
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false, length = 10)
    private UserType userType = UserType.CLIENT;

    /**
     * Account lifecycle. DELETED is a soft-delete — the row is kept but
     * the user cannot log in and is excluded from all public queries.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private UserStatus status = UserStatus.ACTIVE;

    /**
     * How many years the user has been investing.
     * Nullable — user fills this in later from their profile page.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "investment_years", length = 20)
    private InvestmentYears investmentYears;

    /**
     * How much capital the user has invested (INR brackets).
     * Nullable until the user fills in their investor profile.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "investment_amount", length = 20)
    private InvestmentAmount investmentAmount;

    /**
     * Derived investor tier (BEGINNER / INTERMEDIATE / ADVANCED / PRO).
     * Recomputed by InvestorCategoryService every time investmentYears or
     * investmentAmount changes.  Defaults to BEGINNER.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "investor_category", nullable = false, length = 15)
    private InvestorCategory investorCategory = InvestorCategory.BEGINNER;

    /** Set once on INSERT; never updated. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Updated on every save via @PreUpdate. */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ─── JPA lifecycle callbacks ──────────────────────────────────────────

    /** Called by Hibernate before the first INSERT. */
    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    /** Called by Hibernate before every UPDATE. */
    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ─── Getters & Setters ────────────────────────────────────────────────

    public Long getId() { return id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    /** @return true only when this address has actually been verified; NULL reads as false */
    public boolean isEmailVerified() { return Boolean.TRUE.equals(emailVerified); }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }

    public String getEmailVerificationToken() { return emailVerificationToken; }
    public void setEmailVerificationToken(String token) { this.emailVerificationToken = token; }

    public LocalDateTime getEmailVerificationExpiresAt() { return emailVerificationExpiresAt; }
    public void setEmailVerificationExpiresAt(LocalDateTime at) { this.emailVerificationExpiresAt = at; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public UserType getUserType() { return userType; }
    public void setUserType(UserType userType) { this.userType = userType; }

    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }

    public InvestmentYears getInvestmentYears() { return investmentYears; }
    public void setInvestmentYears(InvestmentYears investmentYears) { this.investmentYears = investmentYears; }

    public InvestmentAmount getInvestmentAmount() { return investmentAmount; }
    public void setInvestmentAmount(InvestmentAmount investmentAmount) { this.investmentAmount = investmentAmount; }

    public InvestorCategory getInvestorCategory() { return investorCategory; }
    public void setInvestorCategory(InvestorCategory investorCategory) { this.investorCategory = investorCategory; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
