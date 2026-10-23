package com.equity.payment.controller;

import com.equity.payment.dto.EntitlementsResponse;
import com.equity.payment.service.EntitlementService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/v1/entitlements/me — what the signed-in user may see.
 *
 * Requires a JWT, like every endpoint here except the public plans list and the webhooks. A signed
 * out visitor is not asked about: the browser shows them the free view, and there is no user to
 * resolve a tier for.
 *
 * The browser calls this once per page load and caches it for the session. It is a single row
 * lookup plus a plan lookup, both by primary key.
 */
@RestController
@RequestMapping("/api/v1/entitlements")
public class EntitlementController {

    private final EntitlementService entitlementService;

    public EntitlementController(EntitlementService entitlementService) {
        this.entitlementService = entitlementService;
    }

    /**
     * The caller's own entitlements. There is deliberately no "entitlements of user X" endpoint:
     * one user's plan is nobody else's business, and the only id this ever reads is the one in the
     * token.
     */
    @GetMapping("/me")
    public ResponseEntity<EntitlementsResponse> me() {
        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(entitlementService.forUser(userId));
    }
}
