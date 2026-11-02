package com.equity.watchlist.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Asks payment-service what a user is allowed to do.
 *
 * <h2>Why ask rather than decide</h2>
 * The tier table — what Basic includes, how many companies Advanced allows — lives in exactly one
 * place, {@code EntitlementService} in payment-service. If this service read the subscription rows
 * itself and applied its own numbers, a price or packaging change would have to be made twice and
 * would be wrong in between. So this asks, and caches the answer briefly.
 *
 * <h2>The two deployments</h2>
 * Standalone, {@code payment.service.url} points at payment-service on its own port. Inside
 * main-api-service both halves run in the same JVM and the URL points back at localhost:8080, the
 * same loopback the auth → user call already uses. One code path covers both.
 *
 * <h2>Failing open</h2>
 * If payment-service cannot be reached, this returns limits that allow everything. A billing
 * outage must not stop a paying customer adding a company to their own watchlist — and a free user
 * briefly getting an extra slot costs nothing anybody can measure. The same choice, for the same
 * reason, as the kill switch on the other side.
 */
@Component
public class EntitlementClient {

    private static final Logger logger = LogManager.getLogger(EntitlementClient.class);

    /** How long one user's answer is reused. Long enough to cover a burst, short enough that a
     *  purchase takes effect while the reader is still looking at the page they bought it from. */
    private static final long CACHE_TTL_MS = 60_000;

    /** Returned when payment-service cannot be reached: unlimited, unlocked. */
    private static final Entitlements UNKNOWN =
            new Entitlements("ADVANCED", Map.of(), Map.of(), true);

    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<Long, CacheEntry> cache = new ConcurrentHashMap<>();

    @Value("${payment.service.url:http://localhost:8089}")
    private String paymentServiceUrl;

    @Value("${auth.internal.api-key:}")
    private String internalApiKey;

    /**
     * @param userId the user from the verified JWT
     * @return never null — an unreachable billing service yields {@link #UNKNOWN}
     */
    public Entitlements forUser(Long userId) {
        if (userId == null) return UNKNOWN;

        CacheEntry cached = cache.get(userId);
        if (cached != null && !cached.isStale()) return cached.value;

        Entitlements fresh = fetch(userId);
        cache.put(userId, new CacheEntry(fresh));
        return fresh;
    }

    /** Drops a user's cached answer, so the next check reflects a purchase immediately. */
    public void invalidate(Long userId) {
        cache.remove(userId);
    }

    private Entitlements fetch(Long userId) {
        String url = paymentServiceUrl + "/api/v1/internal/entitlements/" + userId;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Api-Key", internalApiKey);

            ResponseEntity<JsonNode> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);

            JsonNode body = response.getBody();
            if (body == null) return UNKNOWN;

            Map<String, Boolean> features = new java.util.HashMap<>();
            body.path("features").fields().forEachRemaining(e -> features.put(e.getKey(), e.getValue().asBoolean()));

            Map<String, Integer> limits = new java.util.HashMap<>();
            body.path("limits").fields().forEachRemaining(e -> limits.put(e.getKey(), e.getValue().asInt()));

            return new Entitlements(body.path("tier").asText("FREE"), features, limits, false);
        } catch (Exception e) {
            // Logged at warn, not error: this is a degraded state the system is designed to ride
            // out, and it resolves itself as soon as payment-service answers again.
            logger.warn("Could not read entitlements for userId={} from {} — allowing the action: {}",
                        userId, url, e.getMessage());
            return UNKNOWN;
        }
    }

    /**
     * One user's answer.
     *
     * @param tier      FREE, BASIC or ADVANCED
     * @param features  feature key → allowed
     * @param limits    limit key → value, where -1 means no cap
     * @param unknown   true when this is the fail-open placeholder rather than a real answer
     */
    public record Entitlements(String tier,
                               Map<String, Boolean> features,
                               Map<String, Integer> limits,
                               boolean unknown) {

        public boolean can(String feature) {
            return unknown || Boolean.TRUE.equals(features.get(feature));
        }

        /**
         * @param key      limit name
         * @param fallback used when the answer is unknown or the key is missing
         * @return the cap, or -1 for no cap
         */
        public int limit(String key, int fallback) {
            if (unknown) return -1;
            Integer value = limits.get(key);
            return value == null ? fallback : value;
        }
    }

    private static final class CacheEntry {
        private final Entitlements value;
        private final long fetchedAt = System.currentTimeMillis();

        private CacheEntry(Entitlements value) { this.value = value; }

        private boolean isStale() { return System.currentTimeMillis() - fetchedAt > CACHE_TTL_MS; }
    }
}
