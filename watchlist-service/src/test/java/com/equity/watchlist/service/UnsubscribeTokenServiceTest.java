package com.equity.watchlist.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The unsubscribe token is a credential that travels in a URL with no session behind it, so what
 * it refuses matters as much as what it accepts.
 */
class UnsubscribeTokenServiceTest {

    private final UnsubscribeTokenService service =
        new UnsubscribeTokenService("test-secret-value-for-signing");

    @Test
    @DisplayName("a signed token reads back as the same user and company")
    void roundTrip() {
        String token = service.sign(42L, "INFY");

        Optional<UnsubscribeTokenService.Scope> scope = service.read(token);

        assertTrue(scope.isPresent());
        assertEquals(42L, scope.get().userId());
        assertEquals("INFY", scope.get().companyCode());
        assertFalse(scope.get().isAllCompanies());
    }

    @Test
    @DisplayName("the all-alerts token is recognised as such")
    void allScope() {
        Optional<UnsubscribeTokenService.Scope> scope =
            service.read(service.sign(42L, UnsubscribeTokenService.ALL_SCOPE));

        assertTrue(scope.isPresent());
        assertTrue(scope.get().isAllCompanies());
    }

    @Test
    @DisplayName("the token is URL-safe, so a mail client cannot mangle it")
    void urlSafe() {
        for (long id = 1; id < 50; id++) {
            String token = service.sign(id, "RELIANCE");
            assertTrue(token.matches("[A-Za-z0-9_.-]+"), "needs escaping in a URL: " + token);
        }
    }

    @Test
    @DisplayName("editing the payload to target someone else is rejected")
    void tamperedPayloadRejected() {
        String token = service.sign(42L, "INFY");
        String forged = service.sign(43L, "INFY").split("\\.")[0] + "." + token.split("\\.")[1];

        assertTrue(service.read(forged).isEmpty(), "a signature from another payload must not pass");
    }

    @Test
    @DisplayName("a token signed with a different secret is rejected")
    void foreignSecretRejected() {
        String token = new UnsubscribeTokenService("some-other-secret").sign(42L, "INFY");
        assertTrue(service.read(token).isEmpty());
    }

    @Test
    @DisplayName("malformed tokens are refused without throwing")
    void malformedTokens() {
        assertTrue(service.read(null).isEmpty());
        assertTrue(service.read("").isEmpty());
        assertTrue(service.read("no-dot-here").isEmpty());
        assertTrue(service.read(".").isEmpty());
        assertTrue(service.read("!!!not-base64!!!.signature").isEmpty());
        assertTrue(service.read(service.sign(42L, "INFY") + "extra").isEmpty());
    }

    @Test
    @DisplayName("with no secret configured, nothing can be signed and nothing is accepted")
    void unconfigured() {
        UnsubscribeTokenService unconfigured = new UnsubscribeTokenService("  ");

        assertFalse(unconfigured.isConfigured());
        assertThrows(IllegalStateException.class, () -> unconfigured.sign(1L, "INFY"));
        // Including a token that is otherwise perfectly valid — without a secret there is nothing
        // to check it against, and accepting it would mean trusting the URL alone.
        assertTrue(unconfigured.read(service.sign(42L, "INFY")).isEmpty());
    }
}
