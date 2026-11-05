package com.companynews.newsscheduler.alert;

import com.companynews.newsscheduler.service.NewsWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The rules that decide who hears what, and what can never happen twice.
 *
 * <p>The expensive mistakes here are silent: raising the same article again after a restart, or
 * raising months-old news after a re-score. Both are covered, on both channels.
 */
class AlertDispatcherTest {

    private HotNewsCollector collector;
    private SubscriberLookup subscribers;
    private AlertEmailService emailService;
    private NotificationWriter notificationWriter;
    private NewsWorker newsWorker;
    private AlertProperties properties;
    private UnsubscribeTokenSigner signer;
    private AlertDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        collector          = mock(HotNewsCollector.class);
        subscribers        = mock(SubscriberLookup.class);
        emailService       = mock(AlertEmailService.class);
        notificationWriter = mock(NotificationWriter.class);
        newsWorker         = mock(NewsWorker.class);
        properties         = new AlertProperties();
        signer             = mock(UnsubscribeTokenSigner.class);

        set(properties, "enabled", true);
        set(properties, "channel", "notifications");
        set(properties, "threshold", 3.5);
        set(properties, "maxArticleAgeHours", 24);
        set(properties, "quietStart", "22:00");
        set(properties, "quietEnd", "08:00");
        set(properties, "dailyEmailCap", 12);
        set(properties, "maxCompaniesPerCycle", 200);
        set(properties, "baseUrl", "https://niveshflow.com");

        when(signer.isConfigured()).thenReturn(true);
        when(emailService.isEnabled()).thenReturn(true);
        when(emailService.send(any(), any())).thenReturn(true);
        when(notificationWriter.raise(any(), any())).thenReturn(1);
        when(newsWorker.markAlerted(anyString(), any())).thenReturn(1);

        dispatcher = new AlertDispatcher(collector, subscribers, emailService, notificationWriter,
                                         newsWorker, properties, signer);
    }

    /** The properties object is populated by Spring in production; tests set the fields directly. */
    private static void set(Object target, String field, Object value) {
        try {
            var f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new IllegalStateException("no such field: " + field, e);
        }
    }

    private static HotArticle article(String keyword, double score, String link) {
        return new HotArticle(keyword, link, keyword + " in the news", score,
                              Instant.now().toEpochMilli());
    }

    private static SubscriberLookup.Subscriber person(long id) {
        return new SubscriberLookup.Subscriber(id, "user" + id + "@example.com", "User " + id);
    }

    private void newsFor(String keyword, HotArticle... articles) {
        when(collector.hotArticlesFor(eq(keyword), anyLong())).thenReturn(List.of(articles));
    }

    // ═══ In-app notifications — the live channel ═════════════════════════════

    @Nested
    @DisplayName("notification channel")
    class Notifications {

        @Test
        @DisplayName("every subscriber of a company gets a row for the article")
        void raisesPerSubscriber() {
            when(collector.candidateKeywords(anyLong())).thenReturn(List.of("INFY"));
            when(subscribers.subscribersFor(anyCollection(), eq(false)))
                .thenReturn(Map.of("INFY", List.of(person(1), person(2))));
            newsFor("INFY", article("INFY", 4.1, "https://news/1"));
            when(notificationWriter.raise(any(), any())).thenReturn(2);

            assertEquals(2, dispatcher.runCycle());
        }

        @Test
        @DisplayName("an address is never required — that is the whole point of this channel")
        void doesNotRequireVerifiedEmail() {
            when(collector.candidateKeywords(anyLong())).thenReturn(List.of("INFY"));
            when(subscribers.subscribersFor(anyCollection(), anyBoolean())).thenReturn(Map.of());

            dispatcher.runCycle();

            verify(subscribers).subscribersFor(anyCollection(), eq(false));
        }

        @Test
        @DisplayName("delivered articles are stamped, so a later cycle cannot repeat them")
        void stampsDelivered() {
            when(collector.candidateKeywords(anyLong())).thenReturn(List.of("INFY"));
            when(subscribers.subscribersFor(anyCollection(), eq(false)))
                .thenReturn(Map.of("INFY", List.of(person(1))));
            newsFor("INFY", article("INFY", 4.1, "https://news/1"),
                            article("INFY", -3.8, "https://news/2"));

            dispatcher.runCycle();

            ArgumentCaptor<Set<String>> links = ArgumentCaptor.forClass(Set.class);
            verify(newsWorker).markAlerted(eq("INFY"), links.capture());
            assertEquals(Set.of("https://news/1", "https://news/2"), links.getValue());
        }

        @Test
        @DisplayName("an article everyone already had is still stamped, so cycles stop redoing it")
        void stampsEvenWhenNothingNewWasCreated() {
            when(collector.candidateKeywords(anyLong())).thenReturn(List.of("INFY"));
            when(subscribers.subscribersFor(anyCollection(), eq(false)))
                .thenReturn(Map.of("INFY", List.of(person(1))));
            newsFor("INFY", article("INFY", 4.1, "https://news/1"));
            when(notificationWriter.raise(any(), any())).thenReturn(0);   // unique index caught it

            assertEquals(0, dispatcher.runCycle());
            verify(newsWorker).markAlerted(eq("INFY"), any());
        }

        @Test
        @DisplayName("news nobody subscribes to writes nothing and stamps nothing")
        void noSubscribersNoWork() {
            when(collector.candidateKeywords(anyLong())).thenReturn(List.of("INFY"));
            when(subscribers.subscribersFor(anyCollection(), anyBoolean())).thenReturn(Map.of());

            assertEquals(0, dispatcher.runCycle());
            verify(notificationWriter, never()).raise(any(), any());
            verify(newsWorker, never()).markAlerted(anyString(), any());
        }

        @Test
        @DisplayName("no email is sent on this channel, whatever the mail settings say")
        void neverSendsEmail() {
            when(collector.candidateKeywords(anyLong())).thenReturn(List.of("INFY"));
            when(subscribers.subscribersFor(anyCollection(), eq(false)))
                .thenReturn(Map.of("INFY", List.of(person(1))));
            newsFor("INFY", article("INFY", 4.1, "https://news/1"));

            dispatcher.runCycle();

            verify(emailService, never()).send(any(), any());
        }

        @Test
        @DisplayName("quiet hours do not hold a badge")
        void ignoresQuietHours() {
            // 3am: the email channel would hold everything. A panel row disturbs nobody.
            set(properties, "quietStart", "00:00");
            set(properties, "quietEnd", "23:59");
            when(collector.candidateKeywords(anyLong())).thenReturn(List.of("INFY"));
            when(subscribers.subscribersFor(anyCollection(), eq(false)))
                .thenReturn(Map.of("INFY", List.of(person(1))));
            newsFor("INFY", article("INFY", 4.1, "https://news/1"));

            assertEquals(1, dispatcher.runCycle());
        }

        @Test
        @DisplayName("the daily email cap does not apply either")
        void ignoresDailyCap() {
            set(properties, "dailyEmailCap", 1);
            when(collector.candidateKeywords(anyLong())).thenReturn(List.of("INFY"));
            when(subscribers.subscribersFor(anyCollection(), eq(false)))
                .thenReturn(Map.of("INFY", List.of(person(1))));
            newsFor("INFY", article("INFY", 4.1, "https://news/1"));

            assertEquals(1, dispatcher.runCycle());
            assertEquals(1, dispatcher.runCycle());
            assertEquals(1, dispatcher.runCycle(), "a panel does not flood the way an inbox does");
        }

        @Test
        @DisplayName("a missing unsubscribe secret does not block this channel")
        void unsubscribeSecretIrrelevant() {
            // It signs links in emails. There is no link to sign here.
            when(signer.isConfigured()).thenReturn(false);
            when(collector.candidateKeywords(anyLong())).thenReturn(List.of("INFY"));
            when(subscribers.subscribersFor(anyCollection(), eq(false)))
                .thenReturn(Map.of("INFY", List.of(person(1))));
            newsFor("INFY", article("INFY", 4.1, "https://news/1"));

            dispatcher.dispatch();

            verify(notificationWriter).raise(any(), any());
        }

        @Test
        @DisplayName("mail being switched off does not block this channel")
        void emailDisabledIrrelevant() {
            when(emailService.isEnabled()).thenReturn(false);
            when(collector.candidateKeywords(anyLong())).thenReturn(List.of("INFY"));
            when(subscribers.subscribersFor(anyCollection(), eq(false)))
                .thenReturn(Map.of("INFY", List.of(person(1))));
            newsFor("INFY", article("INFY", 4.1, "https://news/1"));

            dispatcher.dispatch();

            verify(notificationWriter).raise(any(), any());
        }
    }

    // ═══ Email — the dormant channel ═════════════════════════════════════════

    @Nested
    @DisplayName("email channel")
    class Email {

        @BeforeEach
        void useEmail() {
            set(properties, "channel", "email");
            // Quiet hours off by default here, so these tests do not depend on the clock. Without
            // this they pass by day and fail every night between 22:00 and 08:00 - which is exactly
            // what happened the first time this suite ran after dark.
            set(properties, "quietStart", "00:00");
            set(properties, "quietEnd", "00:00");
        }

        @Test
        @DisplayName("inside quiet hours an email cycle holds everything")
        void quietHoursHoldEmail() {
            set(properties, "quietStart", "00:00");
            set(properties, "quietEnd", "23:59");          // always quiet, whatever the clock says
            when(collector.candidateKeywords(anyLong())).thenReturn(List.of("INFY"));

            assertEquals(0, dispatcher.runCycle());
            verify(emailService, never()).send(any(), any());
            verify(newsWorker, never()).markAlerted(anyString(), any());
        }

        @Test
        @DisplayName("one person following three companies gets one email, not three")
        void oneEmailPerPerson() {
            when(collector.candidateKeywords(anyLong())).thenReturn(List.of("INFY", "TCS", "HDFCBANK"));
            when(subscribers.subscribersFor(anyCollection(), eq(true))).thenReturn(Map.of(
                "INFY",     List.of(person(1)),
                "TCS",      List.of(person(1)),
                "HDFCBANK", List.of(person(1))
            ));
            newsFor("INFY", article("INFY", 4.1, "l1"));
            newsFor("TCS", article("TCS", 3.9, "l2"));
            newsFor("HDFCBANK", article("HDFCBANK", -4.0, "l3"));

            assertEquals(1, dispatcher.runCycle());

            ArgumentCaptor<List<HotArticle>> captor = ArgumentCaptor.forClass(List.class);
            verify(emailService, times(1)).send(any(), captor.capture());
            assertEquals(3, captor.getValue().size(), "all three companies in one digest");
        }

        @Test
        @DisplayName("only verified addresses are looked up")
        void requiresVerifiedEmail() {
            when(collector.candidateKeywords(anyLong())).thenReturn(List.of("INFY"));
            when(subscribers.subscribersFor(anyCollection(), anyBoolean())).thenReturn(Map.of());

            dispatcher.runCycle();

            verify(subscribers).subscribersFor(anyCollection(), eq(true));
        }

        @Test
        @DisplayName("no notification rows are written on this channel")
        void writesNoNotifications() {
            when(collector.candidateKeywords(anyLong())).thenReturn(List.of("INFY"));
            when(subscribers.subscribersFor(anyCollection(), eq(true)))
                .thenReturn(Map.of("INFY", List.of(person(1))));
            newsFor("INFY", article("INFY", 4.1, "l1"));

            dispatcher.runCycle();

            verify(notificationWriter, never()).raise(any(), any());
        }

        @Test
        @DisplayName("a failed send leaves the article unstamped, so the next cycle retries it")
        void failedSendIsNotMarked() {
            when(emailService.send(any(), any())).thenReturn(false);
            when(collector.candidateKeywords(anyLong())).thenReturn(List.of("INFY"));
            when(subscribers.subscribersFor(anyCollection(), eq(true)))
                .thenReturn(Map.of("INFY", List.of(person(1))));
            newsFor("INFY", article("INFY", 4.1, "l1"));

            assertEquals(0, dispatcher.runCycle());
            verify(newsWorker, never()).markAlerted(anyString(), any());
        }

        @Test
        @DisplayName("with sending switched off, no cycle runs at all")
        void refusesWhenEmailDisabled() {
            when(emailService.isEnabled()).thenReturn(false);

            dispatcher.dispatch();

            verify(collector, never()).candidateKeywords(anyLong());
        }

        @Test
        @DisplayName("without a signing secret nothing is sent — a dead unsubscribe link invites spam reports")
        void refusesWithoutUnsubscribeSecret() {
            when(signer.isConfigured()).thenReturn(false);

            dispatcher.dispatch();

            verify(collector, never()).candidateKeywords(anyLong());
        }

        @Test
        @DisplayName("past the daily cap a recipient is held, and nothing of theirs is stamped")
        void dailyCapHoldsRatherThanDrops() {
            set(properties, "dailyEmailCap", 2);
            when(collector.candidateKeywords(anyLong())).thenReturn(List.of("INFY"));
            when(subscribers.subscribersFor(anyCollection(), eq(true)))
                .thenReturn(Map.of("INFY", List.of(person(1))));
            newsFor("INFY", article("INFY", 4.1, "https://news/a"));

            assertEquals(1, dispatcher.runCycle());
            assertEquals(1, dispatcher.runCycle());
            assertEquals(0, dispatcher.runCycle(), "held, not sent");

            verify(emailService, times(2)).send(any(), any());
            verify(newsWorker, times(2)).markAlerted(anyString(), any());
        }
    }

    // ═══ Shared guards ═══════════════════════════════════════════════════════

    @Test
    @DisplayName("the kill switch stops everything before any query runs")
    void killSwitch() {
        set(properties, "enabled", false);

        dispatcher.dispatch();

        verify(collector, never()).candidateKeywords(anyLong());
        verify(notificationWriter, never()).raise(any(), any());
        verify(emailService, never()).send(any(), any());
    }

    @Test
    @DisplayName("the age cutoff passed to the collector is the configured window")
    void ageCutoffIsApplied() {
        set(properties, "maxArticleAgeHours", 24);
        when(collector.candidateKeywords(anyLong())).thenReturn(List.of());

        dispatcher.runCycle();

        ArgumentCaptor<Long> cutoff = ArgumentCaptor.forClass(Long.class);
        verify(collector, atLeastOnce()).candidateKeywords(cutoff.capture());

        long expected = Instant.now().minusSeconds(24 * 3600).toEpochMilli();
        assertTrue(Math.abs(cutoff.getValue() - expected) < 5000,
                   "cutoff should be ~24h ago; a wider window would let a backfill alert on old news");
    }

    @Test
    @DisplayName("marking failures are survivable — delivery has already happened")
    void markingFailureDoesNotBreakTheCycle() {
        when(newsWorker.markAlerted(anyString(), any())).thenThrow(new RuntimeException("db blip"));
        when(collector.candidateKeywords(anyLong())).thenReturn(List.of("INFY"));
        when(subscribers.subscribersFor(anyCollection(), eq(false)))
            .thenReturn(Map.of("INFY", List.of(person(1))));
        newsFor("INFY", article("INFY", 4.1, "l1"));

        assertEquals(1, dispatcher.runCycle(), "the notification still counted");
    }

    @Test
    @DisplayName("quiet hours crossing midnight are read correctly")
    void quietHoursWindow() {
        assertTrue(properties.isQuietAt(LocalTime.of(23, 30)), "late evening is quiet");
        assertTrue(properties.isQuietAt(LocalTime.of(3, 0)), "the small hours are quiet");
        assertTrue(properties.isQuietAt(LocalTime.of(7, 59)), "just before the end is quiet");
        assertFalse(properties.isQuietAt(LocalTime.of(8, 0)), "the end time itself is not");
        assertFalse(properties.isQuietAt(LocalTime.of(14, 0)), "the market day is not");
    }

    @Test
    @DisplayName("the channel setting is read exactly, and defaults to notifications")
    void channelParsing() {
        set(properties, "channel", "email");
        assertTrue(properties.isEmailChannel());
        assertFalse(properties.isNotificationChannel());

        set(properties, "channel", " EMAIL ");
        assertTrue(properties.isEmailChannel(), "trimmed and case-insensitive");

        set(properties, "channel", "notifications");
        assertTrue(properties.isNotificationChannel());

        // Anything unrecognised falls back to the safe channel rather than silently mailing people.
        set(properties, "channel", "typo");
        assertTrue(properties.isNotificationChannel());
        set(properties, "channel", null);
        assertTrue(properties.isNotificationChannel());
    }
}
