package com.equity.user.controller;

import com.equity.user.dto.InternalResetPasswordRequest;
import com.equity.user.dto.InternalValidateRequest;
import com.equity.user.dto.InternalValidateResponse;
import com.equity.user.service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal REST controller — only callable by auth-service.
 *
 * Base path: /api/v1/internal/users
 *
 * This controller is NOT behind JWT authentication — it is secured instead by
 * a shared secret (X-Internal-Api-Key header) known only to services inside
 * the VPC / private network.  Public traffic never reaches this path because:
 *   - On AWS: the security group blocks port 8087 from the public internet.
 *     Only the EC2 instance itself (where auth-service also runs) can call it.
 *   - On local dev: both services run on localhost, so external access is
 *     blocked by the OS network stack.
 *
 * SecurityConfig permits /api/v1/internal/** at the Spring Security layer
 * (no JWT needed), and this controller performs the API key check manually.
 *
 * Endpoint:
 *   POST /api/v1/internal/users/validate
 *     → Called by auth-service on every login attempt.
 *     → Returns passwordHash so auth-service can verify with BCrypt,
 *       plus userType/status/investorCategory to embed in the JWT claims.
 */
@RestController
@RequestMapping("/api/v1/internal/users")
public class InternalUserController {

    private static final Logger logger = LoggerFactory.getLogger(InternalUserController.class);

    private final UserService userService;
    private final String internalApiKey;

    public InternalUserController(UserService userService,
                                   @Value("${auth.internal.api-key}") String internalApiKey) {
        this.userService    = userService;
        this.internalApiKey = internalApiKey;
    }

    /**
     * POST /api/v1/internal/users/validate
     *
     * Called exclusively by auth-service during the login flow:
     *   1. Client sends username + password to auth-service POST /api/v1/auth/login.
     *   2. auth-service calls THIS endpoint with the username.
     *   3. We return the password hash + role + status.
     *   4. auth-service: BCrypt.matches(plainPassword, hash) to verify credential.
     *   5. auth-service checks status: reject BLOCKED/DELETED.
     *   6. auth-service issues a signed JWT with userId, username, userType, investorCategory.
     *
     * Security:
     *   - X-Internal-Api-Key header must match auth.internal.api-key in properties.
     *   - If not → HTTP 403 (not 401, to avoid leaking that this endpoint exists).
     *
     * @param apiKey  value of the X-Internal-Api-Key header
     * @param request body containing the username to look up
     * @return InternalValidateResponse (includes passwordHash — internal use only)
     */
    @PostMapping("/validate")
    public ResponseEntity<InternalValidateResponse> validate(
            @RequestHeader(value = "X-Internal-Api-Key", required = false) String apiKey,
            @Valid @RequestBody InternalValidateRequest request) {

        // API key guard — reject any caller that doesn't know the shared secret
        if (apiKey == null || !apiKey.equals(internalApiKey)) {
            logger.warn("Internal /validate called with invalid or missing API key");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        InternalValidateResponse response = userService.validateUserInternal(request.getUsername());
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/internal/users/reset-password
     *
     * Called by auth-service after validating the one-time reset token.
     * Looks up the user by email, BCrypt-hashes the new password, and saves.
     *
     * Security: X-Internal-Api-Key header required (same as /validate).
     *
     * Returns: 204 No Content on success
     *          403 if account is blocked
     *          404 if no account found with this email
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(
            @RequestHeader(value = "X-Internal-Api-Key", required = false) String apiKey,
            @Valid @RequestBody InternalResetPasswordRequest request) {

        if (apiKey == null || !apiKey.equals(internalApiKey)) {
            logger.warn("Internal /reset-password called with invalid or missing API key");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        userService.resetPasswordByEmail(request.getEmail(), request.getNewPassword());
        return ResponseEntity.noContent().build();
    }
}
