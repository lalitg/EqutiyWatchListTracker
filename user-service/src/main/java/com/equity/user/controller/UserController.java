package com.equity.user.controller;

import com.equity.user.dto.ChangePasswordRequest;
import com.equity.user.dto.RegisterRequest;
import com.equity.user.dto.UpdateInvestorProfileRequest;
import com.equity.user.dto.UpdateProfileRequest;
import com.equity.user.dto.UserResponse;
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
import org.springframework.web.bind.annotation.RestController;

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

    public UserController(UserService userService) {
        this.userService = userService;
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
}
