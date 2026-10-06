package com.equity.watchlist.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * Signs and reads the tokens in unsubscribe links.
 *
 * <h2>Why a signed token and not a stored one</h2>
 * An unsubscribe link has to work from a mail client, where there is no session — so the link
 * itself must carry the identity. Storing a random token per link would mean a table that grows
 * with every email sent and has to be pruned. A signed token carries the user id and the scope in
 * the open and proves, with a secret only the servers know, that nobody edited it. Nothing to
 * store, nothing to expire, and changing someone else's subscriptions requires forging an HMAC.
 *
 * <h2>Format</h2>
 * <pre>base64url("&lt;userId&gt;:&lt;scope&gt;") + "." + base64url(HMAC-SHA256(payload))</pre>
 * where {@code scope} is a company symbol, or {@code *} for "stop all alerts". The same format is
 * produced by the alert engine in the news service, which signs with the same secret
 * ({@code ALERT_UNSUBSCRIBE_SECRET}) — if the two ever disagree, every unsubscribe link breaks.
 *
 * <h2>Deliberately not expiring</h2>
 * Someone may unsubscribe from a months-old email, and that request is just as valid as a fresh
 * one. An expired unsubscribe link would be worse than useless: the reader's remaining option is
 * to press "spam" instead, which costs far more than honouring a late request.
 */
@Service
public class UnsubscribeTokenService {

    /** Scope value meaning "every alert for this user". */
    public static final String ALL_SCOPE = "*";

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final String ALGORITHM = "HmacSHA256";

    private final String secret;

    public UnsubscribeTokenService(@Value("${alert.unsubscribe.secret:}") String secret) {
        this.secret = secret == null ? "" : secret.trim();
    }

    /** What a valid token says: who, and which company (or all). */
    public record Scope(Long userId, String companyCode) {
        public boolean isAllCompanies() { return ALL_SCOPE.equals(companyCode); }
    }

    /**
     * @param userId      the subscriber
     * @param companyCode a symbol, or {@link #ALL_SCOPE} for every alert
     * @return the token to put in a link
     * @throws IllegalStateException no signing secret is configured
     */
    public String sign(Long userId, String companyCode) {
        requireSecret();
        String payload = userId + ":" + companyCode;
        return ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + mac(payload);
    }

    /**
     * Reads a token, rejecting anything that was edited.
     *
     * @return the scope it authorises, or empty when the token is malformed or unsigned by us
     */
    public Optional<Scope> read(String token) {
        if (secret.isEmpty() || token == null || token.isBlank()) return Optional.empty();

        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) return Optional.empty();

        String encodedPayload = token.substring(0, dot);
        String signature = token.substring(dot + 1);

        String payload;
        try {
            payload = new String(DECODER.decode(encodedPayload), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        if (!constantTimeEquals(mac(payload), signature)) return Optional.empty();

        int colon = payload.indexOf(':');
        if (colon <= 0 || colon == payload.length() - 1) return Optional.empty();
        try {
            Long userId = Long.valueOf(payload.substring(0, colon));
            return Optional.of(new Scope(userId, payload.substring(colon + 1)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** @return whether a secret is configured — links cannot be built without one */
    public boolean isConfigured() {
        return !secret.isEmpty();
    }

    // ─── internals ───────────────────────────────────────────────────────────

    private void requireSecret() {
        if (secret.isEmpty()) {
            throw new IllegalStateException(
                "alert.unsubscribe.secret is not set — unsubscribe links cannot be signed");
        }
    }

    private String mac(String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return ENCODER.encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot sign unsubscribe token: " + e.getMessage(), e);
        }
    }

    /** Compares without revealing, through timing, how much of the signature was right. */
    private static boolean constantTimeEquals(String a, String b) {
        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);
        if (x.length != y.length) return false;
        int diff = 0;
        for (int i = 0; i < x.length; i++) diff |= x[i] ^ y[i];
        return diff == 0;
    }
}
