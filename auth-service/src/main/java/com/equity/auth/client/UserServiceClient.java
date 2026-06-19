package com.equity.auth.client;

import com.equity.auth.dto.SignupRequest;
import com.equity.auth.exception.InvalidCredentialsException;
import com.equity.auth.exception.UserBlockedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * HTTP client for all calls from auth-service → user-service.
 *
 * Three operations:
 *
 * 1. registerUser(SignupRequest)
 *    Called during signup. Forwards the request to user-service
 *    POST /api/v1/users/register which owns the users table and BCrypt logic.
 *    Returns the created user as a Map (raw JSON deserialized).
 *
 * 2. validateUser(identifier)
 *    Called during login. Calls user-service internal
 *    POST /api/v1/internal/users/validate with the X-Internal-Api-Key header.
 *    Returns: { userId, username, passwordHash, userType, status, investorCategory }
 *    auth-service uses passwordHash to verify the password with BCrypt.
 *
 * 3. resetUserPassword(email, newPassword)
 *    Called during the reset-password flow. Calls user-service internal
 *    POST /api/v1/internal/users/reset-password with the X-Internal-Api-Key header.
 *    user-service BCrypt-hashes the new password and saves it.
 *    Throws InvalidCredentialsException if email not found (404).
 *    Throws UserBlockedException if account is blocked (403).
 *
 * Why RestTemplate and not WebClient?
 * RestTemplate is synchronous and simpler. The login flow is sequential
 * (must validate user before issuing token) so async adds no benefit here.
 * RestTemplate is sufficient for a t3.micro with modest load.
 */
@Component
public class UserServiceClient {

    private static final Logger logger = LoggerFactory.getLogger(UserServiceClient.class);

    private final RestTemplate restTemplate;
    private final String userServiceUrl;
    private final String internalApiKey;

    public UserServiceClient(RestTemplate restTemplate,
                              @Value("${auth.user-service.url}") String userServiceUrl,
                              @Value("${auth.internal.api-key}") String internalApiKey) {
        this.restTemplate   = restTemplate;
        this.userServiceUrl = userServiceUrl;
        this.internalApiKey = internalApiKey;
    }

    /**
     * Forwards a signup request to user-service to create the user.
     *
     * Calls: POST http://localhost:8087/api/v1/users/register
     *
     * Builds a request body map matching what user-service's RegisterRequest expects.
     * Returns the raw response body as a Map<String, Object> (user profile fields).
     *
     * @throws RuntimeException wrapping HttpClientErrorException if user-service returns 4xx/5xx
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> registerUser(SignupRequest request) {
        String url = userServiceUrl + "/api/v1/users/register";

        // Build request body matching user-service RegisterRequest fields
        Map<String, Object> body = new HashMap<>();
        body.put("username", request.getUsername());
        body.put("name", request.getName());
        body.put("email", request.getEmail());
        body.put("phoneNumber", request.getPhoneNumber());
        body.put("password", request.getPassword());
        body.put("investmentYears", request.getInvestmentYears());
        body.put("investmentAmount", request.getInvestmentAmount());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
        logger.info("User created via user-service: username={}", request.getUsername());
        return response.getBody();
    }

    /**
     * Calls user-service internal /validate to look up a user by identifier.
     *
     * Calls: POST http://localhost:8087/api/v1/internal/users/validate
     *        Header: X-Internal-Api-Key: <internal-dev-key>
     *
     * The identifier (username/email/phone) is sent in the body.
     * user-service resolves it and returns credentials + role + status.
     *
     * Returns null if user-service returns 404 (user not found).
     * Throws RuntimeException for other errors (5xx from user-service).
     *
     * @param identifier the login identifier (username, email, or phone)
     * @return Map containing: userId, username, passwordHash, userType, status, investorCategory
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> validateUser(String identifier) {
        String url = userServiceUrl + "/api/v1/internal/users/validate";

        Map<String, String> body = new HashMap<>();
        body.put("identifier", identifier);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Api-Key", internalApiKey);

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            return response.getBody();
        } catch (HttpClientErrorException.NotFound ex) {
            // user-service returned 404 — user does not exist
            return null;
        }
    }

    /**
     * Calls user-service internal /reset-password to update a user's password by email.
     *
     * Calls: POST http://localhost:8087/api/v1/internal/users/reset-password
     *        Header: X-Internal-Api-Key: <internal-dev-key>
     *        Body: { "email": "...", "newPassword": "..." }
     *
     * user-service looks up the user by email, BCrypt-hashes the new password, and saves.
     * The plain newPassword is safe to send here because this call is over localhost
     * (internal service-to-service, never crosses the public network).
     *
     * @param email       the email address of the account to reset
     * @param newPassword the new plain-text password (user-service will BCrypt it)
     * @throws InvalidCredentialsException if no account with this email exists (404)
     * @throws UserBlockedException        if the account is blocked (403)
     */
    public void resetUserPassword(String email, String newPassword) {
        String url = userServiceUrl + "/api/v1/internal/users/reset-password";

        Map<String, String> body = new HashMap<>();
        body.put("email", email);
        body.put("newPassword", newPassword);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Api-Key", internalApiKey);

        HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

        try {
            restTemplate.exchange(url, HttpMethod.POST, entity, Void.class);
            logger.info("Password reset via user-service for email={}", email);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new InvalidCredentialsException("No account found with this email address");
        } catch (HttpClientErrorException.Forbidden ex) {
            throw new UserBlockedException("This account is blocked. Password reset is not allowed. Please contact support.");
        }
    }
}
