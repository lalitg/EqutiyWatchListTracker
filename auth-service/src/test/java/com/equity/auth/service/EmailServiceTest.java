package com.equity.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender);
        ReflectionTestUtils.setField(emailService, "fromEmail", "noreply@niveshflow.com");
        ReflectionTestUtils.setField(emailService, "resetPasswordUrl", "https://niveshflow.com/reset-password");
    }

    @Test
    void sends_reset_email_with_token_link_and_correct_headers() {
        emailService.sendPasswordResetEmail("user@example.com", "abc-123-token");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();

        assertThat(sent.getFrom()).isEqualTo("noreply@niveshflow.com");
        assertThat(sent.getTo()).containsExactly("user@example.com");
        assertThat(sent.getSubject()).contains("Reset");
        assertThat(sent.getText())
            .contains("https://niveshflow.com/reset-password?token=abc-123-token")
            .contains("15 minutes");
    }
}