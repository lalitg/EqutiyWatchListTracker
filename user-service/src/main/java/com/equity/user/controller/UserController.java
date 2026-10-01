package com.equity.user.controller;

import com.equity.user.dto.ChangePasswordRequest;
import com.equity.user.dto.RegisterRequest;
import com.equity.user.dto.UpdateInvestorProfileRequest;
import com.equity.user.dto.UpdateProfileRequest;
import com.equity.user.dto.UserResponse;
import com.equity.user.service.EmailVerificationService;
import com.equity.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller for user-facing operations.
 *
 * Base path: /api/v1/users
 *
 * Public endpoints (no JWT required):
 *   POST /register  — create a new account
 *
 * Authenticated endpoints (JWT required — enforced by SecurityConfig + JwtAuthFilter):
 *   GET  /me                   — view own profile
 *   PUT  /me                   — update name / email / phone
 *   PUT  /me/investor-profile  — update investment years + amount (recomputes category)
 *   PUT  /me/password          — change password (requires current password)
 *
 * How userId is obtained in authenticated endpoints:
 *   JwtAuthFilter parses the Bearer token, extracts the `sub` claim as a Long,
 *   and stores it as authentication.getPrincipal().  Controllers cast it to Long.
 *   This means no DB lookup is needed just to identify the caller.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final EmailVerificationService emailVerificationService;

    public UserController(UserService userService,
                          EmailVerificationService emailVerificationService) {
        this.userService              = userService;
        this.emailVerificationService = emailVerificationService;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/users/register
     *
     * Registers a new user.  No JWT needed — this is how a user gets created.
     *
     * Request body: RegisterRequest (username, name, password required;
     *               email, phone, investmentYears, investmentAmount optional)
     *
     * Returns: HTTP 201 Created + UserResponse (passwordHash excluded)
     *
     * Errors:
     *   400 — validation failure (@Valid)
     *   409 — username / email / phone already taken
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = userService.registerUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Authenticated — /me
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/users/me
     *
     * Returns the profile of the currently authenticated user.
     * The userId is read from the JWT (set as principal by JwtAuthFilter).
     *
     * Returns: HTTP 200 + UserResponse
     *
     * Errors:
     *   401 — missing or invalid JWT
     *   404 — user deleted after token was issued (rare race condition)
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getProfile(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(userService.getProfile(userId));
    }

    /**
     * PUT /api/v1/users/me
     *
     * Updates the user's display name and/or contact details.
     * username and password are NOT changeable via this endpoint.
     *
     * Request body: UpdateProfileRequest (name required; email, phone optional)
     *
     * Returns: HTTP 200 + updated UserResponse
     *
     * Errors:
     *   400 — validation failure
     *   401 — missing or invalid JWT
     *   409 — new email / phone already in use by another user
     */
    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateProfile(Authentication authentication,
                                                      @Valid @RequestBody UpdateProfileRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(userService.updateProfile(userId, request));
    }

    /**
     * PUT /api/v1/users/me/investor-profile
     *
     * Updates the user's investment experience and capital, then automatically
     * recomputes and saves their investor_category via the score matrix.
     *
     * Request body: UpdateInvestorProfileRequest (both fields required)
     *
     * Returns: HTTP 200 + updated UserResponse (includes new investorCategory)
     *
     * Errors:
     *   400 — validation failure (null fields)
     *   401 — missing or invalid JWT
     */
    @PutMapping("/me/investor-profile")
    public ResponseEntity<UserResponse> updateInvestorProfile(Authentication authentication,
                                                              @Valid @RequestBody UpdateInvestorProfileRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(userService.updateInvestorProfile(userId, request));
    }

    /**
     * PUT /api/v1/users/me/password
     *
     * Changes the user's password.  The current password must be supplied
     * to prevent a stolen JWT from being used to silently reset the password.
     *
     * Request body: ChangePasswordRequest (currentPassword, newPassword)
     *
     * Returns: HTTP 204 No Content (no body on success)
     *
     * Errors:
     *   400 — wrong current password, or new password too short
     *   401 — missing or invalid JWT
     */
    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(Authentication authentication,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        userService.changePassword(userId, request);
        return ResponseEntity.noContent().build();
    }

    // ────────────────────────────────────────────────────────────────────────────
    // Email verification
    // ────────────────────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/users/me/verify-email
     *
     * Emails the signed-in user a link that proves they own the address on their profile.
     * Nothing can be sent to an unverified address, so this is the gate in front of news alerts.
     *
     * Returns: 202 Accepted, with {@code sent} saying whether a message actually went out.
     *          It is false when the address is already verified, and when mail is switched off in
     *          this environment — in which case the link is written to the service log instead.
     *
     * Errors:
     *   400 — no email on the account, or a link was requested moments ago
     *   401 — missing or invalid JWT
     */
    @PostMapping("/me/verify-email")
    public ResponseEntity<Map<String, Object>> sendVerificationEmail(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        boolean sent = emailVerificationService.requestVerification(userId);
        return ResponseEntity.accepted().body(Map.of(
            "sent", sent,
            "message", sent
                ? "Verification email sent. The link expires in 24 hours."
                : "No email sent — the address is already verified, or email is disabled here."
        ));
    }

    /**
     * GET /api/v1/users/verify-email?token=...
     *
     * Redeems a verification link. Public by necessity: it is opened from a mail client, where
     * there is no session and no token to send — the value in the query string is the credential.
     *
     * Returns: 200 + the verified address, which the confirmation page shows back to the reader.
     *
     * Errors:
     *   400 — the link is unknown, already used, or expired
     */
    @GetMapping("/verify-email")
    public ResponseEntity<Map<String, Object>> verifyEmail(@RequestParam("token") String token) {
        String email = emailVerificationService.verify(token);
        return ResponseEntity.ok(Map.of(
            "verified", true,
            "email", email
        ));
    }
}
