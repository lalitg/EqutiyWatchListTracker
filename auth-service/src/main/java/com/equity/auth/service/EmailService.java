package com.equity.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Sends transactional emails via SMTP (AWS SES in production).
 *
 * Currently the only email is the password-reset link. The raw reset token is
 * delivered here — it is never returned in an HTTP response in production
 * (see {@link AuthService#forgotPassword(String)}).
 *
 * The {@link JavaMailSender} bean is auto-configured by Spring Boot from the
 * {@code spring.mail.*} properties. Because {@code spring.mail.host} always has a
 * default, this bean always exists — even when SMTP credentials are absent (local
 * dev). In that case {@link #sendPasswordResetEmail} throws on send; callers wrap
 * the call so the failure is logged without leaking anything to the user.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${app.email.from}")
    private String fromEmail;

    @Value("${app.email.reset-password-url}")
    private String resetPasswordUrl;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Sends the password-reset email containing the one-time reset link.
     *
     * The link is {@code <app.email.reset-password-url>?token=<urlEncodedRawToken>},
     * which opens the frontend reset-password page with the token pre-filled.
     *
     * @param toEmail  the recipient's email address (the account being reset)
     * @param rawToken the raw, un-hashed reset token to embed in the link
     * @throws org.springframework.mail.MailException if the SMTP send fails
     */
    public void sendPasswordResetEmail(String toEmail, String rawToken) {
        String link = resetPasswordUrl + "?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("Reset your NiveshFlow password");
        message.setText(
            "You (or someone else) requested a password reset for your NiveshFlow account.\n\n" +
            "Click the link below to set a new password:\n" +
            link + "\n\n" +
            "This link expires in 15 minutes and can be used only once.\n\n" +
            "If you did not request this, you can safely ignore this email — your password will not change."
        );

        mailSender.send(message);
        log.info("Password reset email dispatched to {}", toEmail);
    }
}
