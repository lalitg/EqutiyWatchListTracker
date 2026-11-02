package com.equity.watchlist.dto;

import com.equity.watchlist.entity.UserNotification;

import java.time.LocalDateTime;

/**
 * One row of the notification panel, as the browser receives it.
 *
 * <p>A view rather than the entity: {@code userId} never leaves the server, and the panel needs
 * nothing else from the row. {@code unread} is sent as a flag rather than making the browser
 * reason about two nullable timestamps.
 *
 * @param id          addresses this row for read and dismiss
 * @param companyCode the ticker, and where a click leads
 * @param headline    what actually happened — the part the reader judges
 * @param articleUrl  the original story
 * @param score       −5…+5, context beside the ticker
 * @param publishedAt when the article was published
 * @param createdAt   when the notification was raised
 * @param unread      whether it still counts towards the badge
 */
public record NotificationView(Long id,
                               String companyCode,
                               String headline,
                               String articleUrl,
                               Double score,
                               LocalDateTime publishedAt,
                               LocalDateTime createdAt,
                               boolean unread) {

    public static NotificationView from(UserNotification n) {
        return new NotificationView(n.getId(),
                                    n.getCompanyCode(),
                                    n.getHeadline(),
                                    n.getArticleUrl(),
                                    n.getScore(),
                                    n.getPublishedAt(),
                                    n.getCreatedAt(),
                                    n.isUnread());
    }
}
