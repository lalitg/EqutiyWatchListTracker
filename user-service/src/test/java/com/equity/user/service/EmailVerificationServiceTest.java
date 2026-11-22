package com.equity.user.service;

import com.equity.user.entity.User;
import com.equity.user.exception.EmailVerificationException;
import com.equity.user.exception.UserNotFoundException;
import com.equity.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the rules that decide whether an address may be emailed at all.
 *
 * <p>These are not incidental checks: the whole point of verification is that alerts never reach an
 * address nobody proved, because bounces cost the sending reputation every future email depends on.
 */
class EmailVerificationServiceTest {

    private static final long TTL_HOURS = 24;
    private static final int COOLDOWN_SECONDS = 120;

    private UserRepository repository;
    private MailService mailService;
    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        repository  = mock(UserRepository.class);
        mailService = mock(MailService.class);
        when(mailService.send(anyString(), anyString(), anyString())).thenReturn(true);
        when(repository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service = new EmailVerificationService(repository, mailService,
                (int) TTL_HOURS, COOLDOWN_SECONDS, "https://niveshflow.com/verify-email/");
    }

    private User user(String email) {
        User u = new User();
        u.setUsername("ashi");
        u.setName("Ashi");
        u.setEmail(email);
        return u;
    }

    private User stored(String email) {
        User u = user(email);
        when(repository.findById(7L)).thenReturn(Optional.of(u));
        return u;
    }

    // ─── Issuing ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("issues a token with an expiry and emails the link")
    void issuesToken() {
        User u = stored("ashi@example.com");

        assertTrue(service.requestVerification(7L));

        assertNotNull(u.getEmailVerificationToken());
        assertNotNull(u.getEmailVerificationExpiresAt());
        assertTrue(u.getEmailVerificationExpiresAt().isAfter(LocalDateTime.now().plusHours(TTL_HOURS - 1)));
        assertFalse(u.isEmailVerified(), "issuing a link must not verify anything by itself");
        verify(repository).save(u);
        verify(mailService).send(eqTo("ashi@example.com"), anyString(), anyString());
    }

    @Test
    @DisplayName("the emailed link carries the token and points at the site, not this service")
    void linkPointsAtTheSite() {
        User u = stored("ashi@example.com");
        service.requestVerification(7L);

        org.mockito.ArgumentCaptor<String> body = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mailService).send(anyString(), anyString(), body.capture());

        // The trailing slash on the configured base URL must not produce a double slash.
        assertTrue(body.getValue().contains("https://niveshflow.com/verify-email?token="
                                            + u.getEmailVerificationToken()),
                   "link was: " + body.getValue());
    }

    @Test
    @DisplayName("the token is URL-safe, so it survives being pasted out of a mail client")
    void tokenIsUrlSafe() {
        User u = stored("ashi@example.com");
        service.requestVerification(7L);

        String token = u.getEmailVerificationToken();
        assertTrue(token.length() >= 40, "token too short to be unguessable: " + token.length());
        assertTrue(token.matches("[A-Za-z0-9_-]+"), "token needs escaping in a URL: " + token);
    }

    @Test
    @DisplayName("an account with no email address cannot be verified")
    void noEmailIsRejected() {
        stored(null);
        EmailVerificationException ex =
            assertThrows(EmailVerificationException.class, () -> service.requestVerification(7L));
        assertTrue(ex.getMessage().toLowerCase().contains("email"));
        verify(mailService, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("an already-verified address sends nothing and is not an error")
    void alreadyVerifiedIsQuiet() {
        User u = stored("ashi@example.com");
        u.setEmailVerified(true);

        assertFalse(service.requestVerification(7L));
        verify(mailService, never()).send(anyString(), anyString(), anyString());
        verify(repository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("a second request inside the cooldown is refused")
    void cooldownBlocksRapidResend() {
        User u = stored("ashi@example.com");
        service.requestVerification(7L);
        String first = u.getEmailVerificationToken();

        EmailVerificationException ex =
            assertThrows(EmailVerificationException.class, () -> service.requestVerification(7L));
        assertTrue(ex.getMessage().toLowerCase().contains("wait"));
        assertEquals(first, u.getEmailVerificationToken(), "the live link must survive a refused resend");
    }

    @Test
    @DisplayName("once the cooldown has passed, a fresh token replaces the old one")
    void resendAfterCooldownIssuesNewToken() {
        User u = stored("ashi@example.com");
        service.requestVerification(7L);
        String first = u.getEmailVerificationToken();

        // Rewind the issue time by moving the expiry back past the cooldown.
        u.setEmailVerificationExpiresAt(u.getEmailVerificationExpiresAt().minusSeconds(COOLDOWN_SECONDS + 5));

        assertTrue(service.requestVerification(7L));
        assertNotEquals(first, u.getEmailVerificationToken());
    }

    @Test
    @DisplayName("mail being switched off still issues the link, and says nothing was sent")
    void mailDisabledStillIssues() {
        User u = stored("ashi@example.com");
        when(mailService.send(anyString(), anyString(), anyString())).thenReturn(false);

        assertFalse(service.requestVerification(7L), "reports that no message went out");
        assertNotNull(u.getEmailVerificationToken(), "the link must still be usable from the log");
    }

    @Test
    @DisplayName("an unknown user is a 404, not a verification error")
    void unknownUser() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(UserNotFoundException.class, () -> service.requestVerification(99L));
    }

    // ─── Redeeming ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("a valid link verifies the address and burns the token")
    void verifySucceeds() {
        User u = user("ashi@example.com");
        u.setEmailVerificationToken("tok-123");
        u.setEmailVerificationExpiresAt(LocalDateTime.now().plusHours(1));
        when(repository.findByEmailVerificationToken("tok-123")).thenReturn(Optional.of(u));

        assertEquals("ashi@example.com", service.verify("tok-123"));

        assertTrue(u.isEmailVerified());
        assertNull(u.getEmailVerificationToken(), "a link must not work twice");
        assertNull(u.getEmailVerificationExpiresAt());
        verify(repository).save(u);
    }

    @Test
    @DisplayName("an expired link is refused and verifies nothing")
    void expiredTokenRefused() {
        User u = user("ashi@example.com");
        u.setEmailVerificationToken("old");
        u.setEmailVerificationExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(repository.findByEmailVerificationToken("old")).thenReturn(Optional.of(u));

        EmailVerificationException ex =
            assertThrows(EmailVerificationException.class, () -> service.verify("old"));
        assertTrue(ex.getMessage().toLowerCase().contains("expired"));
        assertFalse(u.isEmailVerified());
        verify(repository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("an unknown token is refused without revealing whether it ever existed")
    void unknownTokenRefused() {
        when(repository.findByEmailVerificationToken("nope")).thenReturn(Optional.empty());

        EmailVerificationException ex =
            assertThrows(EmailVerificationException.class, () -> service.verify("nope"));
        assertTrue(ex.getMessage().contains("not valid"));
        assertFalse(ex.getMessage().toLowerCase().contains("expired"),
                    "an unknown token and an expired one must read the same");
    }

    @Test
    @DisplayName("a missing token is refused rather than looked up")
    void blankTokenRefused() {
        assertThrows(EmailVerificationException.class, () -> service.verify(null));
        assertThrows(EmailVerificationException.class, () -> service.verify("  "));
        verify(repository, never()).findByEmailVerificationToken(anyString());
    }

    @Test
    @DisplayName("isVerified reports the stored state")
    void isVerifiedReportsState() {
        User u = stored("ashi@example.com");
        assertFalse(service.isVerified(7L));
        u.setEmailVerified(true);
        assertTrue(service.isVerified(7L));
    }

    /** Readability helper — Mockito's eq() reads poorly beside anyString(). */
    private static String eqTo(String value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
