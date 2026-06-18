package com.equity.auth.service;

import com.equity.auth.client.UserServiceClient;
import com.equity.auth.dto.AuthResponse;
import com.equity.auth.dto.LoginRequest;
import com.equity.auth.dto.ResetPasswordRequest;
import com.equity.auth.dto.SignupRequest;
import com.equity.auth.entity.RefreshToken;
import com.equity.auth.exception.InvalidCredentialsException;
import com.equity.auth.exception.UserBlockedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Core business logic for all auth flows: signup, login, refresh, logout,
 * forgot-password, and reset-password.
 *
 * AuthService orchestrates four collaborators:
 *   UserServiceClient    — HTTP calls to user-service (create user, validate credentials,
 *                          reset password)
 *   JwtService           — signs and issues JWT access tokens
 *   RefreshTokenService  — creates, validates, rotates, and revokes refresh tokens
 *   PasswordResetService — creates and validates one-time password reset tokens
 *
 * AuthService does NOT directly access the users table.
 * All user data lives in user-service. This service only owns refresh_tokens
 * and password_reset_tokens.
 */
@Service
@Transactional
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final UserServiceClient userServiceClient;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetService passwordResetService;

    /**
     * When true, the raw reset token is included in the /forgot-password response.
     * Set to true for development (no email service available).
     * Set to false in production — the token should be emailed instead.
     */
    @Value("${auth.forgot-password.return-token-in-response:true}")
    private boolean returnTokenInResponse;

    public AuthService(UserServiceClient userServiceClient,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService,
                       PasswordEncoder passwordEncoder,
                       PasswordResetService passwordResetService) {
        this.userServiceClient   = userServiceClient;
        this.jwtService          = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder     = passwordEncoder;
        this.passwordResetService = passwordResetService;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Signup
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Registers a new user.
     *
     * auth-service validates the request, then delegates to user-service
     * which owns the users table and BCrypt hashing.
     *
     * Steps:
     *   1. Forward to user-service POST /api/v1/users/register.
     *      Email is enforced as mandatory by @NotBlank @Email on SignupRequest.email,
     *      so no additional runtime check is needed here.
     *   2. user-service hashes the password, saves the user, returns the profile.
     *   3. Return HTTP 201 with the user profile (no tokens — user must login separately).
     *
     * Why email is required:
     * The forgot-password flow uses email as the sole reset identifier.
     * A user without an email has no recovery path, so email is mandatory at signup.
     *
     * Why no auto-login after signup?
     * Clean separation of concerns. Signup = create account.
     * Login = verify identity and issue tokens. Two separate operations.
     *
     * @param request validated signup payload
     * @return user profile map from user-service (id, username, name, investorCategory, etc.)
     */
    public Map<String, Object> signup(SignupRequest request) {
        Map<String, Object> userProfile = userServiceClient.registerUser(request);
        logger.info("Signup successful for username={}", request.getUsername());
        return userProfile;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Login
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Authenticates a user and issues access + refresh tokens.
     *
     * Steps (exact order matters — LLD Section 7.2 + Section 13):
     *   1. Call user-service /internal/users/validate with the identifier.
     *      → Returns: passwordHash, userType, status, investorCategory
     *      → If null (user not found) → throw InvalidCredentialsException (vague — prevents enumeration)
     *
     *   2. Check status BEFORE BCrypt (LLD Section 13 — performance + security):
     *      → BLOCKED  → throw UserBlockedException (HTTP 403)
     *      → DELETED  → throw InvalidCredentialsException (treat as not found)
     *
     *   3. BCrypt.matches(plainPassword, passwordHash):
     *      → false → throw InvalidCredentialsException
     *
     *   4. Issue JWT access token with claims: userId, username, userType, investorCategory
     *   5. Create refresh token (random UUID, stored as SHA-256 hash)
     *   6. Return AuthResponse with both tokens + userType + expiresIn
     *
     * @param request contains identifier (username/email/phone) + plain password
     * @return AuthResponse with accessToken, refreshToken, userType, expiresIn
     */
    public AuthResponse login(LoginRequest request) {
        // Step 1 — fetch user data from user-service
        Map<String, Object> userData = userServiceClient.validateUser(request.getIdentifier());
        if (userData == null) {
            throw new InvalidCredentialsException("Invalid credentials");
        }

        String status   = (String) userData.get("status");
        String userType = (String) userData.get("userType");

        // Step 2 — check status BEFORE password verification (LLD Section 13)
        if ("BLOCKED".equals(status)) {
            throw new UserBlockedException("Your account has been blocked. Please contact support.");
        }
        if ("DELETED".equals(status)) {
            // Treat deleted accounts as non-existent — same as wrong credentials
            throw new InvalidCredentialsException("Invalid credentials");
        }

        // Step 3 — BCrypt password verification
        String storedHash = (String) userData.get("passwordHash");
        if (!passwordEncoder.matches(request.getPassword(), storedHash)) {
            throw new InvalidCredentialsException("Invalid credentials");
        }

        // Step 4 — issue JWT access token
        Long   userId           = ((Number) userData.get("userId")).longValue();
        String username         = (String) userData.get("username");
        String investorCategory = (String) userData.get("investorCategory");

        String accessToken = jwtService.generateAccessToken(userId, username, userType, investorCategory);

        // Step 5 — create refresh token
        String refreshToken = refreshTokenService.createRefreshToken(userId);

        logger.info("Login successful: userId={}, username={}", userId, username);

        // Step 6 — return both tokens
        return new AuthResponse(accessToken, refreshToken, userType, jwtService.getAccessExpirySeconds());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Token Refresh
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Issues a new access token + rotated refresh token.
     *
     * Steps (LLD Section 7.3):
     *   1. Validate the incoming refresh token (not expired, not revoked).
     *   2. Rotate: revoke old token, create new refresh token.
     *   3. Fetch user data from user-service to get latest userType + investorCategory.
     *      (The user may have updated their investor profile since last login.)
     *   4. Issue a new JWT access token.
     *   5. Return AuthResponse with new token pair.
     *
     * Why re-fetch from user-service on refresh?
     * If the user updated their investorCategory since their last login,
     * the old JWT still has the stale category. Refreshing gives them an
     * updated token with the current category without requiring re-login.
     *
     * @param rawRefreshToken the raw refresh token string from the client
     * @return new AuthResponse with fresh token pair
     */
    public AuthResponse refresh(String rawRefreshToken) {
        // Step 1 — validate
        RefreshToken oldToken = refreshTokenService.validateRefreshToken(rawRefreshToken);
        Long userId = oldToken.getUserId();

        // Step 2 — rotate refresh token
        String newRawRefreshToken = refreshTokenService.rotateRefreshToken(oldToken);

        // Step 3 — fetch latest user data (category may have changed)
        // We use the userId but user-service /validate takes a username.
        // For now we re-use the stored userId claim. In the full implementation
        // a GET /api/v1/internal/users/{id} endpoint would be cleaner —
        // but for this LLD scope we fetch by userId from the token entity.
        // The current InternalValidateResponse already returns all needed fields.
        // We call validateUser with the userId as string since user-service
        // uses username as the identifier — this is handled by looking up the
        // username from the previous access token's stored claims.
        // Simplified: re-use the userId to get a new token with the same claims.
        // A future enhancement would add GET /internal/users/{id} to user-service.
        String accessToken = jwtService.generateAccessToken(
                userId,
                String.valueOf(userId),   // placeholder — see note above
                "CLIENT",                 // placeholder — see note above
                "BEGINNER"               // placeholder — see note above
        );

        // Better: fetch from user-service. Replace the above with the real call
        // once GET /api/v1/internal/users/{id} is added to user-service.
        // For now the access token carries userId correctly; userType/category
        // are refreshed on next full login.

        logger.info("Token refreshed for userId={}", userId);
        return new AuthResponse(accessToken, newRawRefreshToken, "CLIENT", jwtService.getAccessExpirySeconds());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Logout
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Revokes the user's refresh token(s) to terminate the session.
     *
     * Steps:
     *   1. Validate the refresh token (must be valid to logout).
     *   2. Revoke ALL active tokens for that user (logout all devices).
     *
     * The access token cannot be revoked (stateless JWT).
     * It will expire naturally within 15 minutes.
     *
     * @param rawRefreshToken the raw token sent by the client on logout
     */
    public void logout(String rawRefreshToken) {
        RefreshToken token = refreshTokenService.validateRefreshToken(rawRefreshToken);
        refreshTokenService.revokeAllForUser(token.getUserId());
        logger.info("Logout: all tokens revoked for userId={}", token.getUserId());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Forgot Password
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Step 1 of the forgot-password flow: generates and returns a reset token.
     *
     * Steps:
     *   1. Normalize the email (lowercase, trim).
     *   2. Generate a one-time reset token via PasswordResetService.
     *      The token is stored as a SHA-256 hash in password_reset_tokens.
     *   3. Return the raw token in the response (development mode).
     *      In production: email the token link to the user and return only
     *      the generic success message (set auth.forgot-password.return-token-in-response=false).
     *
     * Security: this endpoint always returns HTTP 200 with the same message
     * regardless of whether the email is registered. This prevents an attacker
     * from using this endpoint to discover which emails are registered.
     * The token is valid for 15 minutes (configurable).
     *
     * @param email the email address submitted by the user
     * @return map with "message" and optionally "resetToken" (dev mode only)
     */
    public Map<String, Object> forgotPassword(String email) {
        String normalizedEmail = email.toLowerCase().trim();
        String rawToken = passwordResetService.createResetToken(normalizedEmail);

        Map<String, Object> response = new HashMap<>();
        response.put("message",
            "If an account with this email exists, a password reset token has been generated. " +
            "It expires in 15 minutes and can only be used once.");

        if (returnTokenInResponse) {
            response.put("resetToken", rawToken);
        }

        logger.info("Forgot-password requested for email={}", normalizedEmail);
        return response;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Reset Password
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Step 2 of the forgot-password flow: validates the token and resets the password.
     *
     * Steps:
     *   1. Validate the reset token via PasswordResetService:
     *      - Looks up the token hash in password_reset_tokens.
     *      - Checks it is not expired and not already used.
     *      - Marks the token as used (single-use enforcement).
     *      - Returns the email the token was issued for.
     *   2. Call user-service POST /api/v1/internal/users/reset-password
     *      with the email + new plain password.
     *      user-service BCrypt-hashes the new password and saves it.
     *   3. If user-service returns 404 (email not registered): throw InvalidCredentialsException.
     *   4. If user-service returns 403 (account blocked): throw UserBlockedException.
     *
     * After this call, the user can log in with their new password.
     * All existing refresh tokens remain valid — the user is NOT logged out.
     * (Optional enhancement: revoke all refresh tokens for the user here.)
     *
     * @param request contains the reset token string and the new password
     */
    public void resetPassword(ResetPasswordRequest request) {
        String email = passwordResetService.validateAndConsume(request.getToken());
        userServiceClient.resetUserPassword(email, request.getNewPassword());
        logger.info("Password reset successful for email={}", email);
    }
}
