package com.equity.watchlist.controller;

import com.equity.watchlist.service.SubscriptionService;
import com.equity.watchlist.service.UnsubscribeTokenService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

/**
 * The unsubscribe link carried by every alert email.
 *
 * <h2>Why this is public</h2>
 * It is opened from a mail client, where there is no session and no bearer token. The signed token
 * in the query string is the credential: it names the user and the scope, and its HMAC proves it
 * was not edited. Requiring a login here would mean the reader has to sign in to stop email they
 * never wanted — the reliable outcome of that is a spam complaint instead.
 *
 * <h2>Why both GET and POST</h2>
 * GET serves the link a person clicks. POST serves mail clients that implement one-click
 * unsubscribe (RFC 8058), where Gmail and others post to the URL in the {@code List-Unsubscribe}
 * header without the reader ever opening the message.
 */
@RestController
@RequestMapping("/api/alerts")
public class AlertUnsubscribeController {

    private static final Logger logger = LogManager.getLogger(AlertUnsubscribeController.class);

    private final SubscriptionService subscriptionService;
    private final UnsubscribeTokenService tokenService;

    public AlertUnsubscribeController(SubscriptionService subscriptionService,
                                      UnsubscribeTokenService tokenService) {
        this.subscriptionService = subscriptionService;
        this.tokenService        = tokenService;
    }

    /** GET /api/alerts/unsubscribe?token=… — the link in the email body. */
    @GetMapping("/unsubscribe")
    public ResponseEntity<Map<String, Object>> unsubscribe(@RequestParam("token") String token) {
        return apply(token);
    }

    /** POST /api/alerts/unsubscribe?token=… — one-click unsubscribe from the mail client. */
    @PostMapping("/unsubscribe")
    public ResponseEntity<Map<String, Object>> unsubscribePost(@RequestParam("token") String token) {
        return apply(token);
    }

    private ResponseEntity<Map<String, Object>> apply(String token) {
        Optional<UnsubscribeTokenService.Scope> scope = tokenService.read(token);
        if (scope.isEmpty()) {
            logger.warn("Unsubscribe attempted with an invalid token");
            return ResponseEntity.badRequest().body(Map.of(
                "unsubscribed", false,
                "message", "This unsubscribe link is not valid. You can turn alerts off from your "
                         + "account instead."
            ));
        }

        UnsubscribeTokenService.Scope s = scope.get();

        if (s.isAllCompanies()) {
            long removed = subscriptionService.unsubscribeAll(s.userId());
            return ResponseEntity.ok(Map.of(
                "unsubscribed", true,
                "scope", "all",
                "removed", removed,
                "message", "All news alerts are off. You will still receive account emails such as "
                         + "password resets."
            ));
        }

        boolean removed = subscriptionService.unsubscribe(s.userId(), s.companyCode());
        return ResponseEntity.ok(Map.of(
            "unsubscribed", true,
            "scope", s.companyCode(),
            "removed", removed,
            // Reporting "already off" rather than failing: a second click on the same link, or an
            // unsubscribe that crossed with one made in the app, has still produced what was asked.
            "message", removed
                ? "You will no longer receive alerts for " + s.companyCode() + "."
                : "Alerts for " + s.companyCode() + " were already off."
        ));
    }
}
