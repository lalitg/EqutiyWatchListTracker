package com.equity.watchlist.service;

import com.equity.watchlist.entity.CompanyMaster;
import com.equity.watchlist.entity.CompanySubscription;
import com.equity.watchlist.repository.CompanyRepository;
import com.equity.watchlist.repository.CompanySubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Subscribing is the only thing that authorises email about a company, so its rules are tested. */
class SubscriptionServiceTest {

    private CompanySubscriptionRepository subscriptions;
    private CompanyRepository companies;
    private SubscriptionService service;

    @BeforeEach
    void setUp() {
        subscriptions = mock(CompanySubscriptionRepository.class);
        companies     = mock(CompanyRepository.class);
        when(companies.findBySymbol(anyString())).thenReturn(Optional.of(new CompanyMaster()));
        service = new SubscriptionService(subscriptions, companies);
    }

    @Test
    @DisplayName("subscribing stores the symbol in upper case, however it was typed")
    void subscribeNormalises() {
        when(subscriptions.existsByUserIdAndCompanyCode(7L, "INFY")).thenReturn(false);

        assertTrue(service.subscribe(7L, "  infy  "));

        verify(companies).findBySymbol("INFY");
        verify(subscriptions).save(any(CompanySubscription.class));
    }

    @Test
    @DisplayName("subscribing twice is not an error and does not store a second row")
    void subscribeIsIdempotent() {
        when(subscriptions.existsByUserIdAndCompanyCode(7L, "INFY")).thenReturn(true);

        assertFalse(service.subscribe(7L, "INFY"), "reports that nothing new was created");
        verify(subscriptions, never()).save(any(CompanySubscription.class));
    }

    @Test
    @DisplayName("two simultaneous subscribes leave one row, not an error page")
    void concurrentSubscribeIsSwallowed() {
        when(subscriptions.existsByUserIdAndCompanyCode(7L, "INFY")).thenReturn(false);
        when(subscriptions.save(any(CompanySubscription.class)))
            .thenThrow(new DataIntegrityViolationException("uk_subscription_company_user"));

        assertFalse(service.subscribe(7L, "INFY"),
                    "the unique constraint did its job; the user still has what they asked for");
    }

    @Test
    @DisplayName("an unknown symbol is refused rather than stored")
    void unknownSymbolRejected() {
        when(companies.findBySymbol("NOTREAL")).thenReturn(Optional.empty());

        IllegalArgumentException ex =
            assertThrows(IllegalArgumentException.class, () -> service.subscribe(7L, "NOTREAL"));
        assertTrue(ex.getMessage().contains("NOTREAL"));
        verify(subscriptions, never()).save(any(CompanySubscription.class));
    }

    @Test
    @DisplayName("a blank symbol is refused before any lookup")
    void blankSymbolRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.subscribe(7L, "   "));
        assertThrows(IllegalArgumentException.class, () -> service.subscribe(7L, null));
        verify(companies, never()).findBySymbol(anyString());
    }

    @Test
    @DisplayName("there is no cap on how many companies a user may follow")
    void noSubscriptionCap() {
        // Email volume is bounded in the alert engine — one digest per cycle, and a daily ceiling
        // that groups rather than drops. Capping subscriptions would limit the wrong quantity.
        when(subscriptions.existsByUserIdAndCompanyCode(eq(7L), anyString())).thenReturn(false);
        when(subscriptions.countByUserId(7L)).thenReturn(500L);

        assertTrue(service.subscribe(7L, "INFY"));
    }

    @Test
    @DisplayName("unsubscribing reports whether anything was actually removed")
    void unsubscribeReportsRemoval() {
        when(subscriptions.deleteByUserIdAndCompanyCode(7L, "INFY")).thenReturn(1L);
        assertTrue(service.unsubscribe(7L, "infy"));

        when(subscriptions.deleteByUserIdAndCompanyCode(7L, "TCS")).thenReturn(0L);
        assertFalse(service.unsubscribe(7L, "TCS"));
    }

    @Test
    @DisplayName("turning off all alerts removes every subscription at once")
    void unsubscribeAll() {
        when(subscriptions.deleteByUserId(7L)).thenReturn(12L);
        assertEquals(12L, service.unsubscribeAll(7L));
    }

    @Test
    @DisplayName("the list comes back newest first, as symbols")
    void listSymbols() {
        CompanySubscription a = new CompanySubscription("INFY", 7L);
        CompanySubscription b = new CompanySubscription("TCS", 7L);
        b.setCreatedAt(LocalDateTime.now().minusDays(1));
        when(subscriptions.findByUserIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(a, b));

        assertEquals(List.of("INFY", "TCS"), service.listSymbols(7L));
    }
}
