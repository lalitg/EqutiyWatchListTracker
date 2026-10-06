package com.equity.watchlist.controller;

import com.equity.watchlist.service.SubscriptionService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * News-alert subscriptions for the signed-in user.
 *
 * <p>Base path {@code /api/v1/subscriptions}, all authenticated. The user id comes from the JWT
 * principal, exactly as in {@link WatchlistController} — a caller can only ever change their own
 * subscriptions, because the path carries a company symbol and never a user id.
 */
@RestController
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {

    private static final Logger logger = LogManager.getLogger(SubscriptionController.class);

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    /**
     * GET /api/v1/subscriptions
     *
     * @return every symbol this user receives alerts for
     */
    @GetMapping
    public ResponseEntity<List<String>> list(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(subscriptionService.listSymbols(userId));
    }

    /**
     * GET /api/v1/subscriptions/{symbol}
     *
     * <p>Lets the company page render the toggle in the right state on load without fetching the
     * whole list.
     */
    @GetMapping("/{symbol}")
    public ResponseEntity<Map<String, Object>> status(Authentication authentication,
                                                      @PathVariable String symbol) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(Map.of(
            "symbol", symbol.toUpperCase(),
            "subscribed", subscriptionService.isSubscribed(userId, symbol)
        ));
    }

    /**
     * POST /api/v1/subscriptions/{symbol}
     *
     * <p>Idempotent: subscribing to a company you already follow returns 200 with
     * {@code created=false} rather than an error, because the end state is what the user asked for.
     *
     * @return 200 with the resulting state
     * @throws IllegalArgumentException unknown symbol — mapped to 400 by the exception handler
     */
    @PostMapping("/{symbol}")
    public ResponseEntity<Map<String, Object>> subscribe(Authentication authentication,
                                                         @PathVariable String symbol) {
        Long userId = (Long) authentication.getPrincipal();
        boolean created = subscriptionService.subscribe(userId, symbol);
        return ResponseEntity.ok(Map.of(
            "symbol", symbol.toUpperCase(),
            "subscribed", true,
            "created", created
        ));
    }

    /**
     * DELETE /api/v1/subscriptions/{symbol}
     *
     * <p>Also idempotent: removing something already gone is success, not 404.
     */
    @DeleteMapping("/{symbol}")
    public ResponseEntity<Map<String, Object>> unsubscribe(Authentication authentication,
                                                           @PathVariable String symbol) {
        Long userId = (Long) authentication.getPrincipal();
        boolean removed = subscriptionService.unsubscribe(userId, symbol);
        return ResponseEntity.ok(Map.of(
            "symbol", symbol.toUpperCase(),
            "subscribed", false,
            "removed", removed
        ));
    }

    /**
     * DELETE /api/v1/subscriptions
     *
     * <p>Turns off every alert for this user, from inside the app rather than from an email link.
     */
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> unsubscribeAll(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        long removed = subscriptionService.unsubscribeAll(userId);
        logger.info("User {} cleared all alert subscriptions", userId);
        return ResponseEntity.ok(Map.of("removed", removed));
    }
}
