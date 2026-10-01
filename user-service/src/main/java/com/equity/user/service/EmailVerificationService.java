package com.equity.user.service;

import com.equity.user.entity.User;
import com.equity.user.exception.EmailVerificationException;
import com.equity.user.exception.UserNotFoundException;
import com.equity.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

/**
 * Issues and redeems email-verification links.
 *
 * <h2>Why this exists before any alert is sent</h2>
 * {@code users.email} is optional at registration and nothing has ever proved those addresses are
 * real or belong to the person who typed them. Sending news alerts to unproven addresses produces
 * bounces, and a bounce rate above roughly 5% gets an SES account throttled or suspended — which
 * would take down every email the product ever sends, password resets included. Verification is
 * therefore a prerequisite for alerts, not a nicety.
 *
 * <h2>Why the token lives on the user row</h2>
 * A separate token table was considered and rejected: one token per user at a time is the entire
 * requirement, so a table would hold at most one live row per user and add a join for nothing.
 * Three columns on {@code users} carry the same information, and clearing them is how a redeemed
 * link is invalidated.
 *
 * <h2>Token shape</h2>
 * 32 bytes from {@link SecureRandom}, URL-safe Base64, no padding — 43 characters that survive
 * being pasted out of a mail client. Guessing one is not a realistic attack, which is what lets the
 * link work with no session attached.
 */
@Service
@Transactional
public class EmailVerificationService {

    private static final Logger logger = LoggerFactory.getLogger(EmailVerificationService.class);

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final int TOKEN_BYTES = 32;

    private final UserRepository userRepository;
    private final MailService mailService;

    /** How long a verification link stays usable. */
    private final int ttlHours;

    /** How long a user must wait before asking for another link. */
    private final int resendCooldownSeconds;

    /**
     * The page the link opens — the site the user browses, not this service.
     *
     * Configured as a whole URL rather than assembled from a base, matching
     * {@code app.email.reset-password-url} which auth-service already uses for its reset link.
     */
    private final String verifyUrl;

    public EmailVerificationService(UserRepository userRepository,
                                    MailService mailService,
                                    @Value("${user.email-verification.ttl-hours:24}") int ttlHours,
                                    @Value("${user.email-verification.resend-cooldown-seconds:120}") int resendCooldownSeconds,
                                    @Value("${app.email.verify-url:http://localhost:3000/verify-email}") String verifyUrl) {
        this.userRepository        = userRepository;
        this.mailService           = mailService;
        this.ttlHours              = ttlHours;
        this.resendCooldownSeconds = resendCooldownSeconds;
        this.verifyUrl             = stripTrailingSlash(verifyUrl);
    }

    /**
     * Issues a fresh verification link and emails it.
     *
     * <p>Already-verified accounts are a no-op rather than an error: a user who clicks "verify"
     * twice has done nothing wrong, and reporting a failure for a state they already wanted would
     * be confusing.
     *
     * @param userId the caller, taken from their token
     * @return true if a message was handed to the mail server; false when mail is switched off in
     *         this environment (the link is logged instead) or the account is already verified
     * @throws UserNotFoundException        no such user
     * @throws EmailVerificationException   no email on the account, or asked again too soon
     */
    public boolean requestVerification(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new EmailVerificationException(
                "Add an email address to your profile before verifying it");
        }
        if (user.isEmailVerified()) {
            logger.debug("Verification requested for already-verified userId={}", userId);
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime issuedAt = issuedAt(user);
        if (issuedAt != null && issuedAt.plusSeconds(resendCooldownSeconds).isAfter(now)) {
            long wait = Duration.between(now, issuedAt.plusSeconds(resendCooldownSeconds)).toSeconds() + 1;
            throw new EmailVerificationException(
                "A verification email was just sent. Please wait " + wait + " seconds before asking for another");
        }

        String token = newToken();
        user.setEmailVerificationToken(token);
        user.setEmailVerificationExpiresAt(now.plusHours(ttlHours));
        userRepository.save(user);

        logger.info("Verification link issued for userId={} expiresAt={}", userId, user.getEmailVerificationExpiresAt());
        return mailService.send(user.getEmail(), subject(), body(user, token));
    }

    /**
     * Redeems a verification link.
     *
     * <p>The token is cleared on success, so a link works exactly once. An expired or unknown token
     * gets the same message deliberately: telling a stranger which of the two it was would confirm
     * that a token exists.
     *
     * @param token the value from the emailed link
     * @return the address that was verified, for the confirmation screen
     * @throws EmailVerificationException the link is unusable
     */
    public String verify(String token) {
        if (token == null || token.isBlank()) {
            throw new EmailVerificationException("This verification link is not valid. Please request a new one");
        }

        Optional<User> found = userRepository.findByEmailVerificationToken(token);
        if (found.isEmpty()) {
            logger.info("Verification attempted with an unknown token");
            throw new EmailVerificationException("This verification link is not valid. Please request a new one");
        }

        User user = found.get();
        LocalDateTime expiry = user.getEmailVerificationExpiresAt();
        if (expiry == null || expiry.isBefore(LocalDateTime.now())) {
            logger.info("Verification attempted with an expired token for userId={}", user.getId());
            throw new EmailVerificationException("This verification link has expired. Please request a new one");
        }

        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
        user.setEmailVerificationExpiresAt(null);
        userRepository.save(user);

        logger.info("Email verified for userId={}", user.getId());
        return user.getEmail();
    }

    /** @return whether this account's address has been proved */
    @Transactional(readOnly = true)
    public boolean isVerified(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId))
                .isEmailVerified();
    }

    // ─── internals ───────────────────────────────────────────────────────────

    /**
     * When the live token was issued, derived from its expiry.
     *
     * <p>Storing an issued-at column as well would be a fourth column carrying no information: the
     * lifetime is fixed, so expiry minus lifetime is exactly the issue time.
     */
    private LocalDateTime issuedAt(User user) {
        LocalDateTime expiry = user.getEmailVerificationExpiresAt();
        return expiry == null ? null : expiry.minusHours(ttlHours);
    }

    private static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    private String subject() {
        return "Confirm your email address";
    }

    private String body(User user, String token) {
        String link = verifyUrl + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        return "Hi " + user.getName() + ",\n\n"
             + "Confirm this address so NiveshFlow can send you news alerts for the companies you follow:\n\n"
             + link + "\n\n"
             + "The link works once and expires in " + ttlHours + " hours.\n\n"
             + "If you did not create a NiveshFlow account, ignore this message — nothing will be sent to you.\n\n"
             + "— NiveshFlow";
    }

    private static String stripTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
