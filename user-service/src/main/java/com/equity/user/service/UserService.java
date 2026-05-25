package com.equity.user.service;

import com.equity.user.exception.UserBlockedException;
import com.equity.user.dto.ChangePasswordRequest;
import com.equity.user.dto.InternalValidateResponse;
import com.equity.user.dto.RegisterRequest;
import com.equity.user.dto.UpdateInvestorProfileRequest;
import com.equity.user.dto.UpdateProfileRequest;
import com.equity.user.dto.UserResponse;
import com.equity.user.entity.User;
import com.equity.user.enums.InvestorCategory;
import com.equity.user.enums.UserStatus;
import com.equity.user.exception.InvalidPasswordException;
import com.equity.user.exception.UserAlreadyExistsException;
import com.equity.user.exception.UserNotFoundException;
import com.equity.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core business logic for user management.
 *
 * All public methods are @Transactional so that multi-step DB operations
 * (e.g. checking uniqueness + saving) are atomic.  Read-only methods use
 * readOnly=true so Hibernate skips dirty-checking for a small performance gain.
 *
 * Dependencies:
 *   UserRepository         — DB access via Spring Data JPA
 *   PasswordEncoder        — BCrypt hashing (bean defined in AppConfig)
 *   InvestorCategoryService — pure score-matrix computation
 */
@Service
@Transactional
public class UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final InvestorCategoryService investorCategoryService;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       InvestorCategoryService investorCategoryService) {
        this.userRepository          = userRepository;
        this.passwordEncoder         = passwordEncoder;
        this.investorCategoryService = investorCategoryService;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Registration
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Registers a new user.
     *
     * Steps:
     *   1. Guard: reject duplicate username / email / phone (HTTP 409 if any clash).
     *   2. Hash the plain-text password with BCrypt (strength 12 from AppConfig).
     *   3. Compute initial investor_category if years + amount were provided;
     *      otherwise default to BEGINNER.
     *   4. Persist the User entity — Hibernate triggers @PrePersist which sets
     *      created_at and updated_at.
     *   5. Return UserResponse (no passwordHash).
     *
     * @param request validated registration payload
     * @return UserResponse describing the newly created user
     * @throws UserAlreadyExistsException if username/email/phone is taken
     */
    public UserResponse registerUser(RegisterRequest request) {
        // 1. Uniqueness guards
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UserAlreadyExistsException("Username '" + request.getUsername() + "' is already taken");
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()
                && userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("Email '" + request.getEmail() + "' is already registered");
        }
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()
                && userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new UserAlreadyExistsException("Phone number '" + request.getPhoneNumber() + "' is already registered");
        }

        // 2. Build the User entity
        User user = new User();
        user.setUsername(request.getUsername());
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));  // BCrypt

        // 3. Investor profile (optional at registration)
        if (request.getInvestmentYears() != null && request.getInvestmentAmount() != null) {
            user.setInvestmentYears(request.getInvestmentYears());
            user.setInvestmentAmount(request.getInvestmentAmount());
            InvestorCategory category = investorCategoryService.compute(
                    request.getInvestmentYears(), request.getInvestmentAmount());
            user.setInvestorCategory(category);
        }

        User saved = userRepository.save(user);
        logger.info("Registered new user: id={}, username={}", saved.getId(), saved.getUsername());
        return UserResponse.from(saved);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Profile — GET
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the profile of the currently authenticated user.
     *
     * userId comes from the JWT `sub` claim, extracted by JwtAuthFilter and
     * stored as the Authentication principal in SecurityContext.
     *
     * @param userId the authenticated user's ID
     * @throws UserNotFoundException if the ID doesn't exist (should never happen
     *         for a valid JWT, but guards against race conditions)
     */
    @Transactional(readOnly = true)
    public UserResponse getProfile(Long userId) {
        User user = findActiveById(userId);
        return UserResponse.from(user);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Profile — UPDATE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Updates the user's display name, email, and/or phone number.
     *
     * If email or phoneNumber are null in the request, the existing DB values
     * are left unchanged.  If they are non-null, uniqueness is re-checked
     * against other users (excluding the current user's own row).
     *
     * @param userId  authenticated user's ID (from JWT)
     * @param request validated update payload
     * @return updated UserResponse
     */
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findActiveById(userId);

        // Email uniqueness: only check if the value is changing
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            userRepository.findByEmail(request.getEmail())
                    .filter(existing -> !existing.getId().equals(userId))
                    .ifPresent(existing -> {
                        throw new UserAlreadyExistsException("Email '" + request.getEmail() + "' is already in use");
                    });
            user.setEmail(request.getEmail());
        }

        // Phone uniqueness: only check if the value is changing
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
            if (userRepository.existsByPhoneNumber(request.getPhoneNumber())
                    && !request.getPhoneNumber().equals(user.getPhoneNumber())) {
                throw new UserAlreadyExistsException("Phone number '" + request.getPhoneNumber() + "' is already in use");
            }
            user.setPhoneNumber(request.getPhoneNumber());
        }

        user.setName(request.getName());

        User saved = userRepository.save(user);
        logger.info("Updated profile for userId={}", userId);
        return UserResponse.from(saved);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Investor profile — UPDATE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Updates investment_years + investment_amount and recomputes investor_category.
     *
     * This is the only place InvestorCategoryService.compute() is called — ensures
     * the category is always consistent with the profile values.
     *
     * @param userId  authenticated user's ID
     * @param request contains both investmentYears and investmentAmount (both required)
     * @return updated UserResponse with the new investorCategory
     */
    public UserResponse updateInvestorProfile(Long userId, UpdateInvestorProfileRequest request) {
        User user = findActiveById(userId);

        user.setInvestmentYears(request.getInvestmentYears());
        user.setInvestmentAmount(request.getInvestmentAmount());

        InvestorCategory category = investorCategoryService.compute(
                request.getInvestmentYears(), request.getInvestmentAmount());
        user.setInvestorCategory(category);

        User saved = userRepository.save(user);
        logger.info("Updated investor profile for userId={}: years={}, amount={}, category={}",
                userId, request.getInvestmentYears(), request.getInvestmentAmount(), category);
        return UserResponse.from(saved);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Password change
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Changes the user's password after verifying the current one.
     *
     * Security: requires the current password, so a stolen JWT alone is not
     * enough to take over the account by silently resetting the password.
     *
     * @param userId  authenticated user's ID
     * @param request contains currentPassword (plain) and newPassword (plain)
     * @throws InvalidPasswordException if currentPassword doesn't match the hash
     */
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = findActiveById(userId);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new InvalidPasswordException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        logger.info("Password changed for userId={}", userId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal (called by auth-service via /internal/users/validate)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Looks up a user by username for auth-service to use during login.
     *
     * Returns passwordHash so auth-service can verify the credential with BCrypt.
     * Also returns status so auth-service can reject BLOCKED/DELETED accounts.
     *
     * This method bypasses status filtering intentionally — auth-service needs
     * to know the status to reject the login with the right error message.
     *
     * @param username the login handle sent by the client
     * @return InternalValidateResponse with credentials + role + status
     * @throws UserNotFoundException if no user with that username exists
     */
    @Transactional(readOnly = true)
    public InternalValidateResponse validateUserInternal(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + username));

        return new InternalValidateResponse(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getUserType(),
                user.getStatus(),
                user.getInvestorCategory()
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Loads an active (non-deleted) user by ID.
     * Soft-deleted users are treated as non-existent for all public operations.
     *
     * @throws UserNotFoundException if not found or the account is soft-deleted
     */
    private User findActiveById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        if (user.getStatus() == com.equity.user.enums.UserStatus.DELETED) {
            throw new UserNotFoundException("User not found: " + userId);
        }

        return user;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Password reset (called by auth-service via /internal/users/reset-password)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Resets a user's password by their email address.
     * Called by auth-service after validating the one-time reset token.
     *
     * @param email       the registered email of the account
     * @param newPassword the new plain-text password (BCrypt-hashed here before saving)
     * @throws UserNotFoundException   if no active user with this email exists
     * @throws UserBlockedException    if the account is blocked (contact support)
     */
    public void resetPasswordByEmail(String email, String newPassword) {
        User user = userRepository.findByEmail(email.toLowerCase().trim())
                .orElseThrow(() -> new UserNotFoundException("No account found with email: " + email));

        if (user.getStatus() == UserStatus.DELETED) {
            throw new UserNotFoundException("No account found with email: " + email);
        }
        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new UserBlockedException("Account is blocked. Password reset is not allowed.");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        logger.info("Password reset via forgot-password flow for userId={}", user.getId());
    }
}
