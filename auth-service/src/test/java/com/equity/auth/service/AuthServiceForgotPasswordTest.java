package com.equity.auth.service;

import com.equity.auth.client.UserServiceClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuthService#forgotPassword(String)} — the security-critical behavior:
 * the token is emailed (never returned), the response is uniform regardless of account
 * existence, and mail failures never surface to the caller.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceForgotPasswordTest {

    @Mock private UserServiceClient userServiceClient;
    @Mock private JwtService jwtService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PasswordResetService passwordResetService;
    @Mock private EmailService emailService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userServiceClient, jwtService, refreshTokenService,
                passwordEncoder, passwordResetService, emailService);
        // Production-safe default: token is NOT returned in the response.
        ReflectionTestUtils.setField(authService, "returnTokenInResponse", false);
    }

    @Test
    void existing_account_creates_token_sends_email_and_hides_token() {
        when(userServiceClient.validateUser("user@example.com"))
            .thenReturn(Map.of("status", "ACTIVE", "userId", 1));
        when(passwordResetService.createResetToken("user@example.com")).thenReturn("raw-token");

        Map<String, Object> response = authService.forgotPassword("User@Example.com");

        verify(passwordResetService).createResetToken("user@example.com");   // normalized + issued
        verify(emailService).sendPasswordResetEmail("user@example.com", "raw-token");
        assertThat(response).containsKey("message");
        assertThat(response).doesNotContainKey("resetToken");                 // never leaked
    }

    @Test
    void unregistered_email_issues_no_token_and_sends_no_email_but_same_message() {
        when(userServiceClient.validateUser("ghost@example.com")).thenReturn(null);

        Map<String, Object> response = authService.forgotPassword("ghost@example.com");

        verify(passwordResetService, never()).createResetToken(anyString());
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
        assertThat(response).containsKey("message");
        assertThat(response).doesNotContainKey("resetToken");
    }

    @Test
    void deleted_account_is_treated_as_non_existent() {
        when(userServiceClient.validateUser("gone@example.com"))
            .thenReturn(Map.of("status", "DELETED"));

        authService.forgotPassword("gone@example.com");

        verify(passwordResetService, never()).createResetToken(anyString());
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    void mail_failure_is_swallowed_response_stays_uniform() {
        when(userServiceClient.validateUser("user@example.com"))
            .thenReturn(Map.of("status", "ACTIVE"));
        when(passwordResetService.createResetToken("user@example.com")).thenReturn("raw-token");
        doThrow(new MailSendException("SMTP down"))
            .when(emailService).sendPasswordResetEmail(anyString(), anyString());

        Map<String, Object> response = authService.forgotPassword("user@example.com");

        // No exception propagates; caller still gets the generic message.
        assertThat(response).containsKey("message");
        assertThat(response).doesNotContainKey("resetToken");
    }

    @Test
    void dev_mode_flag_true_returns_token_for_local_testing() {
        ReflectionTestUtils.setField(authService, "returnTokenInResponse", true);
        when(userServiceClient.validateUser("user@example.com"))
            .thenReturn(Map.of("status", "ACTIVE"));
        when(passwordResetService.createResetToken("user@example.com")).thenReturn("raw-token");

        Map<String, Object> response = authService.forgotPassword("user@example.com");

        assertThat(response).containsEntry("resetToken", "raw-token");
    }
}