package com.equity.payment.controller;

import com.equity.payment.dto.EntitlementsResponse;
import com.equity.payment.service.EntitlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * GET /api/v1/internal/entitlements/{userId} — the same answer as {@code /entitlements/me}, for a
 * service rather than a browser.
 *
 * <h2>Why this exists next to the public endpoint</h2>
 * watchlist-service has to know a user's limits before it lets them add an eleventh company, and at
 * that moment it holds a verified user id but not the caller's token. Forwarding the bearer token
 * down through the service layer would mean threading an HTTP concern through business code and
 * re-verifying a token that has already been verified once. Asking by id, over a channel that is
 * not reachable from the internet, is the smaller mechanism.
 *
 * <h2>What guards it</h2>
 * The shared {@code X-Internal-Api-Key} header, compared in constant time — the same arrangement
 * user-service uses for {@code /internal/users/validate}. The route is {@code permitAll} in Spring
 * Security precisely because there is no JWT on these calls; this check is the authentication.
 *
 * <p>Read-only and side-effect free: the worst a leaked key buys is the knowledge of which plan a
 * user id is on.
 */
@RestController
@RequestMapping("/api/v1/internal/entitlements")
public class InternalEntitlementController {

    private static final Logger logger = LoggerFactory.getLogger(InternalEntitlementController.class);

    private final EntitlementService entitlementService;
    private final String internalApiKey;

    public InternalEntitlementController(EntitlementService entitlementService,
                                         @Value("${auth.internal.api-key:}") String internalApiKey) {
        this.entitlementService = entitlementService;
        this.internalApiKey     = internalApiKey;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<EntitlementsResponse> forUser(
            @PathVariable Long userId,
            @RequestHeader(value = "X-Internal-Api-Key", required = false) String apiKey) {

        if (!keyMatches(apiKey)) {
            logger.warn("Internal entitlements lookup refused for userId={} - bad or missing key", userId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(entitlementService.forUser(userId));
    }

    /**
     * Constant-time comparison, and an unset key refuses everything.
     *
     * <p>An empty configured key must never mean "allow": a service deployed without the secret
     * would otherwise answer the whole internet.
     */
    private boolean keyMatches(String provided) {
        if (internalApiKey == null || internalApiKey.isBlank() || provided == null) return false;
        return MessageDigest.isEqual(internalApiKey.getBytes(StandardCharsets.UTF_8),
                                     provided.getBytes(StandardCharsets.UTF_8));
    }
}
