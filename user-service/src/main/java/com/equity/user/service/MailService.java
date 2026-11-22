package com.equity.user.service;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Sends plain-text email, and knows how to do nothing safely.
 *
 * <h2>Why sending is switched off by default</h2>
 * {@code app.email.enabled} defaults to {@code false}. A developer running this service locally has no
 * SMTP credentials, and the flows that send mail — email verification today, news alerts next —
 * must still be exercisable end to end. With sending disabled the message is written to the log
 * instead, verification link and all, so the flow can be completed locally by copying that link.
 * Production turns it on explicitly through the environment.
 *
 * <h2>Why the sender is optional</h2>
 * Spring Boot only creates a {@link JavaMailSender} when {@code spring.mail.host} is configured.
 * Taking it as an {@link ObjectProvider} means this service — and therefore the whole application —
 * still starts when mail is not configured at all, which is exactly the state every developer
 * machine is in.
 *
 * <h2>Why failures are swallowed</h2>
 * Returning {@code false} rather than throwing keeps a mail outage from failing the operation that
 * triggered it. A user who asks for a verification email and gets an HTTP 500 assumes their account
 * is broken; the truthful outcome is that the account is fine and the message did not go out, which
 * is what the caller reports. Every failure is logged with the recipient and the cause.
 */
@Service
public class MailService {

    private static final Logger logger = LoggerFactory.getLogger(MailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final boolean enabled;
    private final String fromAddress;
    private final String fromName;

    public MailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                       @Value("${app.email.enabled:false}") boolean enabled,
                       @Value("${app.email.from:noreply@niveshflow.com}") String fromAddress,
                       @Value("${app.email.from-name:NiveshFlow}") String fromName) {
        this.mailSenderProvider = mailSenderProvider;
        this.enabled            = enabled;
        this.fromAddress        = fromAddress;
        this.fromName           = fromName;
    }

    /** @return true when sending is switched on for this environment */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sends one plain-text message.
     *
     * @param to      recipient address
     * @param subject subject line
     * @param body    plain-text body
     * @return {@code true} if the message was handed to the mail server, {@code false} if sending
     *         is disabled, unconfigured, or failed — never throws
     */
    public boolean send(String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            logger.warn("Mail not sent — no recipient address (subject={})", subject);
            return false;
        }

        if (!enabled) {
            // Deliberately at INFO and deliberately including the body: this is the local
            // development path, and the body carries the link the developer needs to continue.
            logger.info("Mail disabled — not sending to={} subject=[{}]\n{}", to, subject, body);
            return false;
        }

        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            logger.error("Mail is enabled but no mail sender is configured — set spring.mail.host. "
                       + "Message to={} subject=[{}] was NOT sent", to, subject);
            return false;
        }

        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            helper.setFrom(new InternetAddress(fromAddress, fromName));
            sender.send(message);
            logger.info("Mail sent to={} subject=[{}]", to, subject);
            return true;
        } catch (Exception e) {
            logger.error("Mail send failed to={} subject=[{}]: {}", to, subject, e.getMessage(), e);
            return false;
        }
    }
}
