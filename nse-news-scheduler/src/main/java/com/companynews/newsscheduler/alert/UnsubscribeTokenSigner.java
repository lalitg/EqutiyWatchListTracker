package com.companynews.newsscheduler.alert;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Signs the unsubscribe links this service puts in alert emails.
 *
 * <h2>Format — must match watchlist-service exactly</h2>
 * <pre>base64url("&lt;userId&gt;:&lt;scope&gt;") + "." + base64url(HMAC-SHA256(payload))</pre>
 * The link is honoured by {@code UnsubscribeTokenService} in the other JVM, which verifies it with
 * the same {@code ALERT_UNSUBSCRIBE_SECRET}. Two rules follow: the secret must be identical in both
 * processes, and this format must not drift — a change on one side silently breaks every
 * unsubscribe link already sitting in people's inboxes.
 *
 * <p>Only signing lives here. This service never reads a token back, because the link is clicked on
 * the website, which is served by the other JVM.
 */
@Service
public class UnsubscribeTokenSigner {

    /** Scope meaning "stop every alert for this user". */
    public static final String ALL_SCOPE = "*";

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final String ALGORITHM = "HmacSHA256";

    private final String secret;

    public UnsubscribeTokenSigner(@Value("${alert.unsubscribe.secret:}") String secret) {
        this.secret = secret == null ? "" : secret.trim();
    }

    /** @return whether links can be signed at all */
    public boolean isConfigured() {
        return !secret.isEmpty();
    }

    /**
     * @param userId the subscriber
     * @param scope  a company symbol, or {@link #ALL_SCOPE}
     * @throws IllegalStateException no secret is configured — sending a mail whose unsubscribe link
     *         cannot work is worse than not sending it
     */
    public String sign(Long userId, String scope) {
        if (secret.isEmpty()) {
            throw new IllegalStateException(
                "alert.unsubscribe.secret is not set - alert emails cannot carry a working "
              + "unsubscribe link, so none may be sent");
        }
        String payload = userId + ":" + scope;
        return ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + mac(payload);
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
}
