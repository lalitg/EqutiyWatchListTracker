package com.equity.user.service;

import com.equity.user.entity.User;
import com.equity.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Acts on bounce and complaint notifications that Amazon SES publishes to SNS.
 *
 * <h2>Why this exists</h2>
 * An address that hard-bounces or reports us as spam must stop receiving mail immediately. Amazon
 * suspends accounts whose bounce rate passes roughly 5% or whose complaint rate passes 0.1%, and a
 * suspension takes down every email the product sends — password resets included, not just alerts.
 * Reacting automatically is also what we told AWS we do when asking for production access.
 *
 * <h2>What "acting" means here</h2>
 * The address is marked unverified and its pending verification token is cleared. That is enough to
 * stop all sending, because nothing is ever sent to an unverified address. The user is not deleted
 * and nothing else about their account changes: a bounce usually means a typo or a mailbox that no
 * longer exists, not a person who did anything wrong. They can correct the address and verify again.
 *
 * <h2>Permanent versus transient</h2>
 * Only <em>permanent</em> bounces unverify. A transient bounce is a full mailbox or a greylisting
 * server — the address is real, and unverifying on a temporary condition would punish a working
 * account for its mail server having a bad afternoon. Complaints always unverify, whatever else
 * they say: someone pressing "spam" is the clearest possible instruction to stop.
 */
@Service
public class SesFeedbackService {

    private static final Logger logger = LoggerFactory.getLogger(SesFeedbackService.class);

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public SesFeedbackService(UserRepository userRepository, ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.objectMapper   = objectMapper;
    }

    /** What the caller must do next, after an SNS envelope has been read. */
    public record Outcome(Kind kind, String subscribeUrl, int addressesAffected) {
        public enum Kind { CONFIRM_SUBSCRIPTION, HANDLED, IGNORED }

        static Outcome confirm(String url)   { return new Outcome(Kind.CONFIRM_SUBSCRIPTION, url, 0); }
        static Outcome handled(int affected) { return new Outcome(Kind.HANDLED, null, affected); }
        static Outcome ignored()             { return new Outcome(Kind.IGNORED, null, 0); }
    }

    /**
     * Reads one SNS envelope and applies whatever it asks for.
     *
     * @param snsEnvelope the raw request body SNS posted
     * @return what happened, and the URL to call back when SNS is confirming a new subscription
     * @throws IllegalArgumentException the body is not JSON we recognise
     */
    @Transactional
    public Outcome handle(String snsEnvelope) {
        JsonNode envelope;
        try {
            envelope = objectMapper.readTree(snsEnvelope);
        } catch (Exception e) {
            throw new IllegalArgumentException("SNS payload is not valid JSON: " + e.getMessage());
        }

        String type = envelope.path("Type").asText("");
        switch (type) {
            case "SubscriptionConfirmation" -> {
                String url = envelope.path("SubscribeURL").asText("");
                logger.info("SNS subscription confirmation received for topic={}",
                            envelope.path("TopicArn").asText(""));
                return Outcome.confirm(url);
            }
            case "UnsubscribeConfirmation" -> {
                logger.warn("SNS unsubscribe confirmation received — bounce notifications may have "
                          + "been turned off for topic={}", envelope.path("TopicArn").asText(""));
                return Outcome.ignored();
            }
            case "Notification" -> {
                return handleNotification(envelope.path("Message").asText(""));
            }
            default -> {
                logger.warn("Ignoring SNS payload of unknown type: {}", type);
                return Outcome.ignored();
            }
        }
    }

    // ─── internals ───────────────────────────────────────────────────────────

    /**
     * The SES event itself, which SNS delivers as a JSON string nested inside its own envelope.
     */
    private Outcome handleNotification(String messageJson) {
        JsonNode message;
        try {
            message = objectMapper.readTree(messageJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("SES message is not valid JSON: " + e.getMessage());
        }

        String notificationType = message.path("notificationType").asText(
                                  message.path("eventType").asText(""));

        return switch (notificationType) {
            case "Bounce"    -> handleBounce(message.path("bounce"));
            case "Complaint" -> handleComplaint(message.path("complaint"));
            default -> {
                logger.debug("Ignoring SES notification of type {}", notificationType);
                yield Outcome.ignored();
            }
        };
    }

    private Outcome handleBounce(JsonNode bounce) {
        String bounceType = bounce.path("bounceType").asText("");
        List<String> recipients = addresses(bounce.path("bouncedRecipients"));

        if (!"Permanent".equalsIgnoreCase(bounceType)) {
            // Real address, temporary problem. Logged so a pattern is visible, but not acted on.
            logger.info("Transient bounce ({}) for {} recipient(s) — left verified",
                        bounceType, recipients.size());
            return Outcome.ignored();
        }

        int affected = unverifyAll(recipients, "permanent bounce");
        return Outcome.handled(affected);
    }

    private Outcome handleComplaint(JsonNode complaint) {
        List<String> recipients = addresses(complaint.path("complainedRecipients"));
        int affected = unverifyAll(recipients, "spam complaint");
        return Outcome.handled(affected);
    }

    private List<String> addresses(JsonNode recipientArray) {
        List<String> out = new ArrayList<>();
        if (recipientArray.isArray()) {
            for (JsonNode node : recipientArray) {
                String address = node.path("emailAddress").asText("");
                if (!address.isBlank()) out.add(address.trim());
            }
        }
        return out;
    }

    /**
     * Marks each address unverified.
     *
     * <p>An address we do not recognise is normal rather than an error: SES reports on everything
     * the account sends, and a user may have changed their address since the message went out.
     */
    private int unverifyAll(List<String> addresses, String reason) {
        int affected = 0;
        for (String address : addresses) {
            Optional<User> found = userRepository.findByEmail(address);
            if (found.isEmpty()) {
                logger.info("SES {} for an address with no matching user", reason);
                continue;
            }

            User user = found.get();
            if (!user.isEmailVerified() && user.getEmailVerificationToken() == null) {
                logger.info("SES {} for userId={} — already unverified, nothing to do",
                            reason, user.getId());
                continue;
            }

            user.setEmailVerified(false);
            user.setEmailVerificationToken(null);
            user.setEmailVerificationExpiresAt(null);
            userRepository.save(user);
            affected++;
            logger.warn("Address unverified after {} — userId={}. No further email will be sent "
                      + "to it until the user verifies again", reason, user.getId());
        }
        return affected;
    }
}
