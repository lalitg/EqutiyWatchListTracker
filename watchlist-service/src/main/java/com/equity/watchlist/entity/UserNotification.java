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
 * One thing worth telling one user about one company.
 *
 * <h2>Why this table exists when the email design needed none</h2>
 * Email carried its sent-state on the article itself — one stamp served everybody. Notifications
 * cannot: <em>read</em> and <em>dismissed</em> are per person, and two people following INFY can
 * have the same article unread and dismissed at the same moment. That state has nowhere to live
 * but a row each.
 *
 * <h2>Who writes it</h2>
 * The news service, in the other JVM, with plain SQL — it owns no entity for this table, the same
 * way it owns none for {@code company_subscriptions}. This class is the single JPA mapping, and
 * everything the reader does (list, read, dismiss) goes through it.
 *
 * <h2>Dismissing keeps the row</h2>
 * A dismissal sets {@code dismissedAt} rather than deleting, so the unique constraint still blocks
 * the same article arriving again for that user. The nightly prune removes everything older than a
 * week regardless of state.
 */
@Entity
@Table(name = "user_notifications",
       uniqueConstraints = @UniqueConstraint(name = "uk_notification_user_article",
                                             columnNames = {"user_id", "article_url"}),
       indexes = @Index(name = "idx_notification_user_active",
                        columnList = "user_id, dismissed_at, created_at"))
public class UserNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** {@code users.id} of the reader this belongs to. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** NSE symbol, matching {@code company_subscriptions.company_code}. */
    @Column(name = "company_code", nullable = false, length = 50)
    private String companyCode;

    /**
     * The headline, stored rather than looked up.
     *
     * <p>Copied at the moment the notification is raised so the panel needs no join into the news
     * JSONB, and so a notification still reads correctly after retention has removed the article
     * from the news row.
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String headline;

    /** Where the reader goes for the actual story — and the per-user deduplication key. */
    @Column(name = "article_url", nullable = false, columnDefinition = "TEXT")
    private String articleUrl;

    /** Sentiment on the −5…+5 scale, shown beside the ticker as context. */
    @Column(nullable = false)
    private Double score;

    /** When the article was published, not when we noticed it. */
    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Set when the reader opens it. Null means it still counts towards the badge. */
    @Column(name = "read_at")
    private LocalDateTime readAt;

    /** Set when the reader presses ×. Set means it never appears again. */
    @Column(name = "dismissed_at")
    private LocalDateTime dismissedAt;

    protected UserNotification() {
        // for JPA
    }

    public Long getId() { return id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getCompanyCode() { return companyCode; }
    public void setCompanyCode(String companyCode) { this.companyCode = companyCode; }

    public String getHeadline() { return headline; }
    public void setHeadline(String headline) { this.headline = headline; }

    public String getArticleUrl() { return articleUrl; }
    public void setArticleUrl(String articleUrl) { this.articleUrl = articleUrl; }

    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }

    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getReadAt() { return readAt; }
    public void setReadAt(LocalDateTime readAt) { this.readAt = readAt; }

    public LocalDateTime getDismissedAt() { return dismissedAt; }
    public void setDismissedAt(LocalDateTime dismissedAt) { this.dismissedAt = dismissedAt; }

    /** @return true while this still counts towards the bell's badge */
    public boolean isUnread() { return readAt == null && dismissedAt == null; }
}
