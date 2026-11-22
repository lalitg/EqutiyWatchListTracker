package com.equity.watchlist.service;

import com.equity.watchlist.dto.NotificationView;
import com.equity.watchlist.entity.UserNotification;
import com.equity.watchlist.repository.UserNotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What the bell counts, what the panel shows, and what another user can reach — which is nothing.
 */
class NotificationServiceTest {

    private UserNotificationRepository repository;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        repository = mock(UserNotificationRepository.class);
        when(repository.save(any(UserNotification.class))).thenAnswer(i -> i.getArgument(0));
        service = new NotificationService(repository);
    }

    /** The entity has no public constructor — it is built by JPA and by the news service's SQL. */
    private static UserNotification notification(Long userId) {
        try {
            var ctor = UserNotification.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            UserNotification n = ctor.newInstance();
            n.setUserId(userId);
            n.setCompanyCode("INFY");
            n.setHeadline("Infosys wins a large deal");
            n.setArticleUrl("https://news/1");
            n.setScore(4.1);
            n.setPublishedAt(LocalDateTime.now().minusHours(1));
            n.setCreatedAt(LocalDateTime.now());
            return n;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ─── Listing and counting ────────────────────────────────────────────────

    @Test
    @DisplayName("the panel asks for twenty by default")
    void defaultLimit() {
        when(repository.findByUserIdAndDismissedAtIsNullOrderByCreatedAtDesc(eq(7L), any()))
            .thenReturn(List.of(notification(7L)));

        assertEquals(1, service.list(7L, null).size());

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByUserIdAndDismissedAtIsNullOrderByCreatedAtDesc(eq(7L), page.capture());
        assertEquals(20, page.getValue().getPageSize());
    }

    @Test
    @DisplayName("an absurd limit is clamped rather than served")
    void limitIsClamped() {
        when(repository.findByUserIdAndDismissedAtIsNullOrderByCreatedAtDesc(eq(7L), any()))
            .thenReturn(List.of());

        service.list(7L, 100000);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByUserIdAndDismissedAtIsNullOrderByCreatedAtDesc(eq(7L), page.capture());
        assertEquals(100, page.getValue().getPageSize(), "a crafted limit cannot ask for the table");
    }

    @Test
    @DisplayName("a zero or negative limit falls back to the default")
    void nonsenseLimit() {
        when(repository.findByUserIdAndDismissedAtIsNullOrderByCreatedAtDesc(eq(7L), any()))
            .thenReturn(List.of());

        service.list(7L, 0);
        service.list(7L, -5);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(repository, org.mockito.Mockito.times(2))
            .findByUserIdAndDismissedAtIsNullOrderByCreatedAtDesc(eq(7L), page.capture());
        page.getAllValues().forEach(p -> assertEquals(20, p.getPageSize()));
    }

    @Test
    @DisplayName("the view carries what the panel draws, and never the user id")
    void viewShape() {
        when(repository.findByUserIdAndDismissedAtIsNullOrderByCreatedAtDesc(eq(7L), any()))
            .thenReturn(List.of(notification(7L)));

        NotificationView view = service.list(7L, null).get(0);

        assertEquals("INFY", view.companyCode());
        assertEquals("Infosys wins a large deal", view.headline());
        assertEquals("https://news/1", view.articleUrl());
        assertEquals(4.1, view.score());
        assertTrue(view.unread());
        assertNotNull(view.publishedAt());
    }

    @Test
    @DisplayName("the badge counts unread, undismissed items")
    void unreadCount() {
        when(repository.countByUserIdAndReadAtIsNullAndDismissedAtIsNull(7L)).thenReturn(3L);
        assertEquals(3L, service.unreadCount(7L));
    }

    // ─── Reading ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("opening an item clears it from the badge but keeps it in the list")
    void markRead() {
        UserNotification n = notification(7L);
        when(repository.findByIdAndUserId(5L, 7L)).thenReturn(Optional.of(n));

        assertTrue(service.markRead(7L, 5L));

        assertNotNull(n.getReadAt());
        assertFalse(n.isUnread());
        assertTrue(n.getDismissedAt() == null, "reading is not dismissing — people reread things");
    }

    @Test
    @DisplayName("re-reading something already read changes nothing")
    void markReadTwice() {
        UserNotification n = notification(7L);
        n.setReadAt(LocalDateTime.now().minusMinutes(5));
        when(repository.findByIdAndUserId(5L, 7L)).thenReturn(Optional.of(n));

        assertFalse(service.markRead(7L, 5L));
        verify(repository, never()).save(any(UserNotification.class));
    }

    @Test
    @DisplayName("mark-all-read clears the badge in one statement")
    void markAllRead() {
        when(repository.markAllRead(eq(7L), any())).thenReturn(9);
        assertEquals(9, service.markAllRead(7L));
    }

    // ─── Dismissing ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("dismissing hides it for good and takes it off the badge")
    void dismiss() {
        UserNotification n = notification(7L);
        when(repository.findByIdAndUserId(5L, 7L)).thenReturn(Optional.of(n));

        assertTrue(service.dismiss(7L, 5L));

        assertNotNull(n.getDismissedAt());
        assertNotNull(n.getReadAt(), "dismissed without opening still means seen and decided");
        assertFalse(n.isUnread());
    }

    @Test
    @DisplayName("dismissing twice is success, not an error")
    void dismissTwice() {
        UserNotification n = notification(7L);
        n.setDismissedAt(LocalDateTime.now().minusMinutes(1));
        when(repository.findByIdAndUserId(5L, 7L)).thenReturn(Optional.of(n));

        assertFalse(service.dismiss(7L, 5L), "nothing changed, but the state is what was asked for");
        verify(repository, never()).save(any(UserNotification.class));
    }

    @Test
    @DisplayName("an already-read item can still be dismissed")
    void dismissAfterReading() {
        UserNotification n = notification(7L);
        LocalDateTime readAt = LocalDateTime.now().minusMinutes(5);
        n.setReadAt(readAt);
        when(repository.findByIdAndUserId(5L, 7L)).thenReturn(Optional.of(n));

        assertTrue(service.dismiss(7L, 5L));
        assertEquals(readAt, n.getReadAt(), "the original read time is not overwritten");
    }

    // ─── Scoping ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("another user's notification is simply not found")
    void cannotTouchSomeoneElses() {
        // The repository query filters on user id, so a wrong owner returns empty rather than a
        // row to refuse. Nothing tells the caller whether that id exists at all.
        when(repository.findByIdAndUserId(anyLong(), anyLong())).thenReturn(Optional.empty());

        assertFalse(service.markRead(99L, 5L));
        assertFalse(service.dismiss(99L, 5L));
        verify(repository, never()).save(any(UserNotification.class));
    }
}
