package com.equity.watchlist.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * One user's standing request to be emailed about one company's news.
 *
 * <h2>Why this is separate from the watchlist</h2>
 * A watchlist is a reading list — people put companies there to look at them, including ones they
 * are merely curious about. An alert is a push into someone's inbox, and the two intentions are not
 * the same: the whole watchlist arriving by email is exactly the outcome that gets a sender marked
 * as spam. Subscribing is therefore a separate, deliberate act, and the only thing that authorises
 * email about a company.
 *
 * <h2>The unique constraint is load-bearing</h2>
 * A double-clicked Subscribe button, or two tabs open on the same company, would otherwise create
 * two rows and send two copies of every alert. The constraint makes a repeat subscribe harmless.
 */
@Entity
@Table(name = "company_subscriptions",
       uniqueConstraints = @UniqueConstraint(name = "uk_subscription_company_user",
                                             columnNames = {"company_code", "user_id"}),
       indexes = @Index(name = "idx_subscription_company", columnList = "company_code"))
public class CompanySubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * NSE symbol, matching {@code watchlist.company_code} and {@code company_news.keyword}.
     *
     * <p>Stored as the symbol rather than a foreign key to {@code company_master} because the news
     * service — which runs in a different JVM and owns none of these tables — looks subscriptions up
     * by the same keyword it files news under.
     */
    @Column(name = "company_code", nullable = false, length = 50)
    private String companyCode;

    /** {@code users.id} of the subscriber. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected CompanySubscription() {
        // for JPA
    }

    public CompanySubscription(String companyCode, Long userId) {
        this.companyCode = companyCode;
        this.userId = userId;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public String getCompanyCode() { return companyCode; }
    public void setCompanyCode(String companyCode) { this.companyCode = companyCode; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
