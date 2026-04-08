package com.equity.auth.controller;

import com.equity.auth.dto.AuthResponse;
import com.equity.auth.dto.LoginRequest;
import com.equity.auth.dto.LogoutRequest;
import com.equity.auth.dto.RefreshRequest;
import com.equity.auth.dto.SignupRequest;
import com.equity.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller for all authentication endpoints.
 *
 * Base path: /api/v1/auth
 *
 * All endpoints are PUBLIC (no JWT required) — see SecurityConfig.
 *
 * Endpoints:
 *   POST /signup   — register a new user
 *   POST /login    — authenticate and receive access + refresh tokens
 *   POST /refresh  — exchange a refresh token for a new token pair
 *   POST /logout   — revoke the refresh token (terminate session)
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * POST /api/v1/auth/signup
     *
     * Registers a new user. Delegates to user-service to create the account.
     * Does NOT issue tokens — the user must call /login separately.
     *
     * Request body: SignupRequest (username, name, email/phone, password required;
     *               investmentYears, investmentAmount optional)
     *
     * Returns: HTTP 201 + user profile (from user-service)
     *
     * Errors:
     *   400 — validation failure (@Valid) or neither email nor phone provided
     *   409 — username / email / phone already taken (from user-service)
     */
    @PostMapping("/signup")
    public ResponseEntity<Map<String, Object>> signup(@Valid @RequestBody SignupRequest request) {
        Map<String, Object> userProfile = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(userProfile);
    }

    /**
     * POST /api/v1/auth/login
     *
     * Authenticates the user and issues a JWT access token + refresh token.
     *
     * Request body: { "identifier": "alice", "password": "..." }
     *   identifier can be username, email, or phone number.
     *
     * Returns: HTTP 200 + AuthResponse:
     *   { "accessToken": "eyJ...", "refreshToken": "uuid...",
     *     "userType": "CLIENT", "expiresIn": 900 }
     *
     * Errors:
     *   400 — validation failure
     *   401 — wrong password or identifier not found
     *   403 — account is BLOCKED
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/auth/refresh
     *
     * Exchanges a valid refresh token for a new access token + rotated refresh token.
     * The old refresh token is revoked — it cannot be reused.
     *
     * Request body: { "refreshToken": "uuid..." }
     *
     * Returns: HTTP 200 + new AuthResponse with fresh token pair
     *
     * Errors:
     *   400 — validation failure
     *   401 — refresh token expired, revoked, or not found
     *
     * Frontend behaviour (LLD Section 16):
     *   If any API returns 401, frontend silently calls /refresh once.
     *   If /refresh also returns 401, frontend clears localStorage and
     *   redirects to /#/login.
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        AuthResponse response = authService.refresh(request.getRefreshToken());
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/auth/logout
     *
     * Revokes the refresh token, terminating the user's session on all devices.
     *
     * Request body: { "refreshToken": "uuid..." }
     *
     * Returns: HTTP 204 No Content
     *
     * Errors:
     *   400 — validation failure
     *   401 — refresh token already expired or revoked
     *
     * Note: the access token cannot be revoked (stateless JWT). It expires
     * naturally within 15 minutes. The frontend must clear it from localStorage.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }
}
