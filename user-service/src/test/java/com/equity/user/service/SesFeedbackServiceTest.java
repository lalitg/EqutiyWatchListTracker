package com.equity.user.service;

import com.equity.user.entity.User;
import com.equity.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers what happens to an address after SES reports it bounced or was marked as spam.
 *
 * <p>Getting this wrong is expensive in both directions: ignoring a hard bounce risks the sending
 * account, and unverifying on a temporary failure locks a real user out of their own alerts.
 */
class SesFeedbackServiceTest {

    private UserRepository repository;
    private SesFeedbackService service;

    @BeforeEach
    void setUp() {
        repository = mock(UserRepository.class);
        when(repository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new SesFeedbackService(repository, new ObjectMapper());
    }

    private User verifiedUser(String email) {
        User u = new User();
        u.setUsername("ashi");
        u.setName("Ashi");
        u.setEmail(email);
        u.setEmailVerified(true);
        when(repository.findByEmail(email)).thenReturn(Optional.of(u));
        return u;
    }

    /** An SES event, wrapped the way SNS delivers it: JSON inside a JSON string. */
    private static String sns(String innerJson) {
        String escaped = innerJson.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"Type\":\"Notification\","
             + "\"TopicArn\":\"arn:aws:sns:ap-south-1:123:niveshflow-ses-events\","
             + "\"Message\":\"" + escaped + "\"}";
    }

    private static String bounce(String type, String email) {
        return "{\"notificationType\":\"Bounce\",\"bounce\":{\"bounceType\":\"" + type + "\","
             + "\"bouncedRecipients\":[{\"emailAddress\":\"" + email + "\"}]}}";
    }

    private static String complaint(String email) {
        return "{\"notificationType\":\"Complaint\",\"complaint\":{"
             + "\"complainedRecipients\":[{\"emailAddress\":\"" + email + "\"}]}}";
    }

    // ─── Bounces ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a permanent bounce unverifies the address and clears any pending link")
    void permanentBounceUnverifies() {
        User u = verifiedUser("gone@example.com");
        u.setEmailVerificationToken("pending");
        u.setEmailVerificationExpiresAt(LocalDateTime.now().plusHours(2));

        SesFeedbackService.Outcome outcome = service.handle(sns(bounce("Permanent", "gone@example.com")));

        assertEquals(SesFeedbackService.Outcome.Kind.HANDLED, outcome.kind());
        assertEquals(1, outcome.addressesAffected());
        assertFalse(u.isEmailVerified());
        assertNull(u.getEmailVerificationToken());
        assertNull(u.getEmailVerificationExpiresAt());
        verify(repository).save(u);
    }

    @Test
    @DisplayName("a transient bounce changes nothing — the address is real, the problem is not")
    void transientBounceIsLeftAlone() {
        User u = verifiedUser("full.mailbox@example.com");

        SesFeedbackService.Outcome outcome = service.handle(sns(bounce("Transient", "full.mailbox@example.com")));

        assertEquals(SesFeedbackService.Outcome.Kind.IGNORED, outcome.kind());
        assertTrue(u.isEmailVerified(), "a full mailbox must not cost the user their alerts");
        verify(repository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("every recipient of a multi-address bounce is handled")
    void multipleRecipients() {
        User a = verifiedUser("one@example.com");
        User b = verifiedUser("two@example.com");
        String message = "{\"notificationType\":\"Bounce\",\"bounce\":{\"bounceType\":\"Permanent\","
                       + "\"bouncedRecipients\":[{\"emailAddress\":\"one@example.com\"},"
                       + "{\"emailAddress\":\"two@example.com\"}]}}";

        assertEquals(2, service.handle(sns(message)).addressesAffected());
        assertFalse(a.isEmailVerified());
        assertFalse(b.isEmailVerified());
    }

    // ─── Complaints ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("a spam complaint always unverifies, whatever else it says")
    void complaintUnverifies() {
        User u = verifiedUser("annoyed@example.com");

        SesFeedbackService.Outcome outcome = service.handle(sns(complaint("annoyed@example.com")));

        assertEquals(1, outcome.addressesAffected());
        assertFalse(u.isEmailVerified());
    }

    // ─── Things that must not blow up ────────────────────────────────────────

    @Test
    @DisplayName("an address we do not recognise is normal, not an error")
    void unknownAddressIsQuiet() {
        when(repository.findByEmail("stranger@example.com")).thenReturn(Optional.empty());

        SesFeedbackService.Outcome outcome = service.handle(sns(bounce("Permanent", "stranger@example.com")));

        assertEquals(0, outcome.addressesAffected());
        verify(repository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("an already-unverified address is not written again")
    void alreadyUnverified() {
        User u = verifiedUser("gone@example.com");
        u.setEmailVerified(false);

        assertEquals(0, service.handle(sns(bounce("Permanent", "gone@example.com"))).addressesAffected());
        verify(repository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("a delivery notification is ignored rather than acted on")
    void deliveryIgnored() {
        String message = "{\"notificationType\":\"Delivery\",\"delivery\":{\"recipients\":[\"x@example.com\"]}}";
        assertEquals(SesFeedbackService.Outcome.Kind.IGNORED, service.handle(sns(message)).kind());
    }

    @Test
    @DisplayName("a subscription confirmation hands back the URL to call")
    void subscriptionConfirmation() {
        String envelope = "{\"Type\":\"SubscriptionConfirmation\","
                        + "\"TopicArn\":\"arn:aws:sns:ap-south-1:123:niveshflow-ses-events\","
                        + "\"SubscribeURL\":\"https://sns.ap-south-1.amazonaws.com/?Action=Confirm&Token=abc\"}";

        SesFeedbackService.Outcome outcome = service.handle(envelope);

        assertEquals(SesFeedbackService.Outcome.Kind.CONFIRM_SUBSCRIPTION, outcome.kind());
        assertTrue(outcome.subscribeUrl().startsWith("https://sns.ap-south-1.amazonaws.com/"));
    }

    @Test
    @DisplayName("rubbish in the body is reported, not swallowed silently")
    void malformedPayload() {
        assertThrows(IllegalArgumentException.class, () -> service.handle("not json at all"));
        assertThrows(IllegalArgumentException.class,
                     () -> service.handle("{\"Type\":\"Notification\",\"Message\":\"also not json\"}"));
    }

    @Test
    @DisplayName("an unsubscribe confirmation is noticed but changes nothing")
    void unsubscribeConfirmation() {
        String envelope = "{\"Type\":\"UnsubscribeConfirmation\",\"TopicArn\":\"arn:aws:sns:x\"}";
        assertEquals(SesFeedbackService.Outcome.Kind.IGNORED, service.handle(envelope).kind());
    }
}
