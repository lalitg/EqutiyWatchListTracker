package com.companynews.newsscheduler.alert;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What the recipient actually reads.
 *
 * <p>The content rules are not cosmetic: the model is right about three times in four, so an email
 * that leads with a number instead of the headline asks the reader to trust something unreliable,
 * and one without a working unsubscribe link earns a spam complaint.
 */
class AlertEmailServiceTest {

    private AlertProperties properties;
    private UnsubscribeTokenSigner signer;
    private AlertEmailService service;

    @BeforeEach
    void setUp() {
        properties = new AlertProperties();
        set(properties, "baseUrl", "https://niveshflow.com");
        set(properties, "fromAddress", "noreply@niveshflow.com");
        set(properties, "fromName", "NiveshFlow");

        signer = mock(UnsubscribeTokenSigner.class);
        when(signer.sign(org.mockito.ArgumentMatchers.anyLong(), anyString()))
            .thenAnswer(inv -> "tok-" + inv.getArgument(0) + "-" + inv.getArgument(1));

        @SuppressWarnings("unchecked")
        ObjectProvider<org.springframework.mail.javamail.JavaMailSender> provider =
            mock(ObjectProvider.class);
        service = new AlertEmailService(provider, properties, signer, false);
    }

    private static void set(Object target, String field, Object value) {
        try {
            var f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception e) {
            throw new IllegalStateException(field, e);
        }
    }

    private static HotArticle article(String keyword, double score, String headline) {
        return new HotArticle(keyword, "https://news.example.com/" + keyword, headline, score,
                              Instant.now().toEpochMilli());
    }

    private static final SubscriberLookup.Subscriber ASHI =
        new SubscriberLookup.Subscriber(7L, "ashi@example.com", "Ashi");

    @Test
    @DisplayName("the subject names the companies, because an inbox shows only the subject")
    void subjectNamesCompanies() {
        String subject = service.subject(List.of(article("INFY", 4.1, "a"),
                                                 article("HDFCBANK", -3.9, "b")));

        assertTrue(subject.contains("INFY"));
        assertTrue(subject.contains("HDFCBANK"));
        assertTrue(subject.startsWith("2 alerts"));
    }

    @Test
    @DisplayName("one article reads as singular")
    void singularSubject() {
        assertTrue(service.subject(List.of(article("INFY", 4.1, "a"))).startsWith("1 alert:"));
    }

    @Test
    @DisplayName("a long list is trimmed rather than filling the subject line")
    void longSubjectIsTrimmed() {
        String subject = service.subject(List.of(
            article("A", 4.0, "x"), article("B", 4.0, "x"), article("C", 4.0, "x"),
            article("D", 4.0, "x"), article("E", 4.0, "x")));

        assertTrue(subject.contains("and 2 more"), subject);
    }

    @Test
    @DisplayName("good and bad news are separated, and the headline leads")
    void bodySeparatesGoodAndBad() {
        String body = service.body(ASHI, List.of(
            article("INFY", 4.1, "Infosys wins a large deal"),
            article("HDFCBANK", -3.9, "Regulator flags lapses")));

        assertTrue(body.contains("GOOD NEWS"));
        assertTrue(body.contains("BAD NEWS"));
        assertTrue(body.contains("Infosys wins a large deal"));
        assertTrue(body.contains("Regulator flags lapses"));
        assertTrue(body.indexOf("GOOD NEWS") < body.indexOf("BAD NEWS"));
    }

    @Test
    @DisplayName("an all-positive digest carries no empty Bad news heading")
    void noEmptySections() {
        String body = service.body(ASHI, List.of(article("INFY", 4.1, "good only")));

        assertTrue(body.contains("GOOD NEWS"));
        assertFalse(body.contains("BAD NEWS"));
    }

    @Test
    @DisplayName("every article carries its link and a signed score")
    void articlesCarryLinkAndScore() {
        String body = service.body(ASHI, List.of(article("INFY", 4.1, "headline")));

        assertTrue(body.contains("https://news.example.com/INFY"));
        assertTrue(body.contains("+4.1"), "a positive score is signed so the scale is unambiguous");
    }

    @Test
    @DisplayName("the disclaimer is present in every digest")
    void disclaimerAlwaysPresent() {
        String body = service.body(ASHI, List.of(article("INFY", 4.1, "headline")));

        assertTrue(body.contains("not investment advice"));
        assertTrue(body.contains("read the article"));
    }

    @Test
    @DisplayName("unsubscribe links are offered per company and for everything")
    void unsubscribeLinks() {
        String body = service.body(ASHI, List.of(article("INFY", 4.1, "a"),
                                                 article("TCS", -4.0, "b")));

        assertTrue(body.contains("Stop alerts for INFY"));
        assertTrue(body.contains("Stop alerts for TCS"));
        assertTrue(body.contains("Stop all alerts"));
        assertTrue(body.contains("tok-7-*"), "the all-scope token is signed for this user");
        assertTrue(body.contains("/unsubscribe?token="));
    }

    @Test
    @DisplayName("a recipient with no name is still addressed properly")
    void missingNameIsHandled() {
        String body = service.body(new SubscriberLookup.Subscriber(7L, "x@example.com", null),
                                   List.of(article("INFY", 4.1, "a")));

        assertTrue(body.startsWith("Hi there,"));
    }

    @Test
    @DisplayName("with email disabled nothing is sent and the caller is told so")
    void disabledSendsNothing() {
        assertFalse(service.send(ASHI, List.of(article("INFY", 4.1, "a"))));
    }

    @Test
    @DisplayName("an empty digest is never sent")
    void emptyDigestNotSent() {
        assertFalse(service.send(ASHI, List.of()));
    }
}
