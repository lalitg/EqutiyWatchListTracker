package com.equity.auth.controller;

import com.equity.auth.dto.AuthResponse;
import com.equity.auth.dto.ForgotPasswordRequest;
import com.equity.auth.dto.LoginRequest;
import com.equity.auth.dto.LogoutRequest;
import com.equity.auth.dto.RefreshRequest;
import com.equity.auth.dto.ResetPasswordRequest;
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
 *   POST /signup           — register a new user
 *   POST /login            — authenticate and receive access + refresh tokens
 *   POST /refresh          — exchange a refresh token for a new token pair
 *   POST /logout           — revoke the refresh token (terminate session)
 *   POST /forgot-password  — generate a one-time password reset token
 *   POST /reset-password   — consume the reset token and set a new password
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
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/v1/auth/forgot-password
     *
     * Step 1 of the forgot-password flow.
     * Generates a one-time reset token valid for 15 minutes.
     *
     * Request body: { "email": "user@example.com" }
     *
     * Returns: HTTP 200 + {
     *   "message": "If an account with this email exists...",
     *   "resetToken": "abc123..."   ← included in dev mode only
     * }
     *
     * Security: always returns 200 regardless of whether the email is registered.
     * This prevents attackers from discovering which emails are in the system.
     *
     * In production: set auth.forgot-password.return-token-in-response=false
     * and wire up an email service to deliver the token to the user's inbox.
     *
     * Errors:
     *   400 — missing or invalid email format
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, Object>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        Map<String, Object> response = authService.forgotPassword(request.getEmail());
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/auth/reset-password
     *
     * Step 2 of the forgot-password flow.
     * Validates the reset token and updates the user's password.
     *
     * Request body: {
     *   "token": "the-reset-token-from-step-1",
     *   "newPassword": "NewPass123!"
     * }
     *
     * Returns: HTTP 204 No Content on success.
     *
     * After this call, the user can log in with their new password.
     *
     * Errors:
     *   400 — missing fields or new password too short
     *   401 — token is invalid, expired, or already used
     *   401 — no account found with the email this token was issued for
     *   403 — the account is blocked (contact support)
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }
}
