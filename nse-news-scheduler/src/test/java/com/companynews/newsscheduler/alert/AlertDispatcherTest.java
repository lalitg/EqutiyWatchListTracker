package com.companynews.newsscheduler.alert;

import com.companynews.newsscheduler.service.NewsWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
 * The rules that decide who receives what, and what can never happen twice.
 *
 * <p>The expensive mistakes here are silent: mailing the same article again after a restart, or
 * mailing months-old news after a re-score. Both are covered.
 */
class AlertDispatcherTest {

    private HotNewsCollector collector;
    private SubscriberLookup subscribers;
    private AlertEmailService emailService;
    private NewsWorker newsWorker;
    private AlertProperties properties;
    private UnsubscribeTokenSigner signer;
    private AlertDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        collector    = mock(HotNewsCollector.class);
        subscribers  = mock(SubscriberLookup.class);
        emailService = mock(AlertEmailService.class);
        newsWorker   = mock(NewsWorker.class);
        properties   = new AlertProperties();
        signer       = mock(UnsubscribeTokenSigner.class);

        set(properties, "enabled", true);
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
        when(newsWorker.markAlerted(anyString(), any())).thenReturn(1);

        dispatcher = new AlertDispatcher(collector, subscribers, emailService, newsWorker,
                                         properties, signer);
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

    // ─── Grouping ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("one person following three companies gets one email, not three")
    void oneEmailPerPerson() {
        when(collector.candidateKeywords(anyLong())).thenReturn(List.of("INFY", "TCS", "HDFCBANK"));
        when(subscribers.subscribersFor(anyCollection())).thenReturn(Map.of(
            "INFY",     List.of(person(1)),
            "TCS",      List.of(person(1)),
            "HDFCBANK", List.of(person(1))
        ));
        when(collector.hotArticlesFor(eq("INFY"), anyLong())).thenReturn(List.of(article("INFY", 4.1, "l1")));
        when(collector.hotArticlesFor(eq("TCS"), anyLong())).thenReturn(List.of(article("TCS", 3.9, "l2")));
        when(collector.hotArticlesFor(eq("HDFCBANK"), anyLong())).thenReturn(List.of(article("HDFCBANK", -4.0, "l3")));

        assertEquals(1, dispatcher.runCycle());

        ArgumentCaptor<List<HotArticle>> captor = ArgumentCaptor.forClass(List.class);
        verify(emailService, times(1)).send(any(), captor.capture());
        assertEquals(3, captor.getValue().size(), "all three companies in one digest");
    }

    @Test
    @DisplayName("each subscriber of the same company gets their own email")
    void eachSubscriberGetsOne() {
        when(collector.candidateKeywords(anyLong())).thenReturn(List.of("INFY"));
        when(subscribers.subscribersFor(anyCollection()))
            .thenReturn(Map.of("INFY", List.of(person(1), person(2), person(3))));
        when(collector.hotArticlesFor(eq("INFY"), anyLong())).thenReturn(List.of(article("INFY", 4.1, "l1")));

        assertEquals(3, dispatcher.runCycle());
    }

    @Test
    @DisplayName("news nobody subscribes to sends nothing and marks nothing")
    void noSubscribersNoWork() {
        when(collector.candidateKeywords(anyLong())).thenReturn(List.of("INFY"));
        when(subscribers.subscribersFor(anyCollection())).thenReturn(Map.of());

        assertEquals(0, dispatcher.runCycle());
        verify(emailService, never()).send(any(), any());
        verify(newsWorker, never()).markAlerted(anyString(), any());
    }

    // ─── Never twice ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("delivered articles are stamped, so a later cycle cannot repeat them")
    void deliveredArticlesAreMarked() {
        when(collector.candidateKeywords(anyLong())).thenReturn(List.of("INFY"));
        when(subscribers.subscribersFor(anyCollection())).thenReturn(Map.of("INFY", List.of(person(1))));
        when(collector.hotArticlesFor(eq("INFY"), anyLong()))
            .thenReturn(List.of(article("INFY", 4.1, "https://news/1"),
                                article("INFY", -3.8, "https://news/2")));

        dispatcher.runCycle();

        ArgumentCaptor<Set<String>> links = ArgumentCaptor.forClass(Set.class);
        verify(newsWorker).markAlerted(eq("INFY"), links.capture());
        assertEquals(Set.of("https://news/1", "https://news/2"), links.getValue());
    }

    @Test
    @DisplayName("a failed send leaves the article unstamped, so the next cycle retries it")
    void failedSendIsNotMarked() {
        when(emailService.send(any(), any())).thenReturn(false);
        when(collector.candidateKeywords(anyLong())).thenReturn(List.of("INFY"));
        when(subscribers.subscribersFor(anyCollection())).thenReturn(Map.of("INFY", List.of(person(1))));
        when(collector.hotArticlesFor(eq("INFY"), anyLong())).thenReturn(List.of(article("INFY", 4.1, "l1")));

        assertEquals(0, dispatcher.runCycle());
        verify(newsWorker, never()).markAlerted(anyString(), any());
    }

    @Test
    @DisplayName("marking failures are survivable — the mail has already gone")
    void markingFailureDoesNotBreakTheCycle() {
        when(newsWorker.markAlerted(anyString(), any())).thenThrow(new RuntimeException("db blip"));
        when(collector.candidateKeywords(anyLong())).thenReturn(List.of("INFY"));
        when(subscribers.subscribersFor(anyCollection())).thenReturn(Map.of("INFY", List.of(person(1))));
        when(collector.hotArticlesFor(eq("INFY"), anyLong())).thenReturn(List.of(article("INFY", 4.1, "l1")));

        assertEquals(1, dispatcher.runCycle(), "the send still counted");
    }

    // ─── Switches and guards ─────────────────────────────────────────────────

    @Test
    @DisplayName("the kill switch stops everything before any query runs")
    void killSwitch() {
        set(properties, "enabled", false);

        dispatcher.dispatch();

        verify(collector, never()).candidateKeywords(anyLong());
        verify(emailService, never()).send(any(), any());
    }

    @Test
    @DisplayName("with email sending switched off, no cycle runs at all")
    void refusesWhenEmailDisabled() {
        // Otherwise the same articles are found every cycle, a digest nobody receives is logged,
        // and nothing is ever stamped - an infinite repeat dressed up as work.
        when(emailService.isEnabled()).thenReturn(false);

        dispatcher.dispatch();

        verify(collector, never()).candidateKeywords(anyLong());
        verify(emailService, never()).send(any(), any());
    }

    @Test
    @DisplayName("without a signing secret nothing is sent — a dead unsubscribe link invites spam reports")
    void refusesWithoutUnsubscribeSecret() {
        when(signer.isConfigured()).thenReturn(false);

        dispatcher.dispatch();

        verify(collector, never()).candidateKeywords(anyLong());
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
                   "cutoff should be ~24h ago; a wider window would let a backfill mail old news");
    }

    // ─── Quiet hours ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("quiet hours crossing midnight are read correctly")
    void quietHoursWindow() {
        assertTrue(properties.isQuietAt(LocalTime.of(23, 30)), "late evening is quiet");
        assertTrue(properties.isQuietAt(LocalTime.of(3, 0)), "the small hours are quiet");
        assertTrue(properties.isQuietAt(LocalTime.of(7, 59)), "just before the end is quiet");
        assertFalse(properties.isQuietAt(LocalTime.of(8, 0)), "the end time itself is not");
        assertFalse(properties.isQuietAt(LocalTime.of(14, 0)), "the market day is not");
        assertFalse(properties.isQuietAt(LocalTime.of(21, 59)), "just before the start is not");
    }

    @Test
    @DisplayName("a same-day quiet window also works")
    void sameDayQuietWindow() {
        set(properties, "quietStart", "01:00");
        set(properties, "quietEnd", "06:00");

        assertTrue(properties.isQuietAt(LocalTime.of(3, 0)));
        assertFalse(properties.isQuietAt(LocalTime.of(23, 0)));
        assertFalse(properties.isQuietAt(LocalTime.of(8, 0)));
    }

    @Test
    @DisplayName("quiet hours can be switched off entirely")
    void quietHoursDisabled() {
        set(properties, "quietStart", "00:00");
        set(properties, "quietEnd", "00:00");

        assertFalse(properties.isQuietAt(LocalTime.of(3, 0)));
    }

    // ─── Pacing ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("past the daily cap a recipient is held, and nothing of theirs is stamped")
    void dailyCapHoldsRatherThanDrops() {
        set(properties, "dailyEmailCap", 2);
        when(collector.candidateKeywords(anyLong())).thenReturn(List.of("INFY"));
        when(subscribers.subscribersFor(anyCollection())).thenReturn(Map.of("INFY", List.of(person(1))));
        when(collector.hotArticlesFor(eq("INFY"), anyLong()))
            .thenReturn(List.of(article("INFY", 4.1, "https://news/a")));

        assertEquals(1, dispatcher.runCycle());
        assertEquals(1, dispatcher.runCycle());
        // Third cycle: the cap is reached and the last email was moments ago.
        assertEquals(0, dispatcher.runCycle(), "held, not sent");

        verify(emailService, times(2)).send(any(), any());
        // Crucially the held article stays unstamped, so it goes out in the hourly batch rather
        // than being lost.
        verify(newsWorker, times(2)).markAlerted(anyString(), any());
    }
}
