package com.equity.user.controller;

import com.equity.user.service.SesFeedbackService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Receives Amazon SES bounce and complaint notifications, delivered by SNS.
 *
 * <h2>Why the body arrives as a String</h2>
 * SNS posts JSON with {@code Content-Type: text/plain}. Declaring a typed body would make Spring
 * reject every notification with HTTP 415 before any code ran - a failure that looks like silence,
 * since SNS retries quietly and then gives up.
 *
 * <h2>How this endpoint is protected</h2>
 * SNS cannot send custom headers, so the usual internal API-key header is not available here. The
 * endpoint instead carries a shared secret in its URL and checks the topic it claims to come from.
 * Both live in the environment; without a configured secret the endpoint refuses everything, so an
 * unconfigured deployment cannot be poked at.
 *
 * <p>This is deliberately short of verifying SNS's message signature, which is the stronger control
 * and worth adding. Forging a notification still requires knowing a random secret that appears only
 * in the AWS console subscription and the server's env file, and the worst a forgery achieves is
 * marking an address unverified - the user re-verifies and nothing is lost.
 *
 * <h2>Why it answers 200 to things it ignores</h2>
 * A non-2xx reply makes SNS retry, and then eventually disable the subscription. A payload we do
 * not act on is not a delivery failure, so it is acknowledged and logged.
 */
@RestController
@RequestMapping("/api/v1/internal/ses")
public class SesNotificationController {

    private static final Logger logger = LoggerFactory.getLogger(SesNotificationController.class);

    private final SesFeedbackService feedbackService;
    private final String sharedSecret;
    private final String expectedTopicArn;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public SesNotificationController(SesFeedbackService feedbackService,
                                     @Value("${app.email.sns.secret:}") String sharedSecret,
                                     @Value("${app.email.sns.topic-arn:}") String expectedTopicArn) {
        this.feedbackService  = feedbackService;
        this.sharedSecret     = sharedSecret == null ? "" : sharedSecret.trim();
        this.expectedTopicArn = expectedTopicArn == null ? "" : expectedTopicArn.trim();
    }

    /**
     * POST /api/v1/internal/ses/notifications?key=...
     *
     * @param key  the shared secret, from the subscription URL configured in SNS
     * @param body the raw SNS envelope
     * @return 200 when handled or deliberately ignored, 403 when the secret does not match
     */
    @PostMapping(value = "/notifications", consumes = MediaType.ALL_VALUE)
    public ResponseEntity<String> receive(@RequestParam(name = "key", required = false) String key,
                                          @RequestBody(required = false) String body) {

        if (sharedSecret.isEmpty()) {
            logger.warn("SES notification rejected - no shared secret configured on this server");
            return ResponseEntity.status(403).body("not configured");
        }
        if (!constantTimeEquals(sharedSecret, key == null ? "" : key.trim())) {
            logger.warn("SES notification rejected - wrong or missing key");
            return ResponseEntity.status(403).body("forbidden");
        }
        if (body == null || body.isBlank()) {
            return ResponseEntity.ok("empty");
        }

        try {
            if (!topicAllowed(body)) {
                logger.warn("SES notification rejected - unexpected topic ARN");
                return ResponseEntity.status(403).body("forbidden");
            }

            SesFeedbackService.Outcome outcome = feedbackService.handle(body);

            if (outcome.kind() == SesFeedbackService.Outcome.Kind.CONFIRM_SUBSCRIPTION) {
                ConfirmResult result = confirmSubscription(outcome.subscribeUrl());
                return ResponseEntity.ok(result.ok()
                    ? "subscription confirmed"
                    : "subscription NOT confirmed - " + result.detail());
            }
            return ResponseEntity.ok(outcome.kind().name().toLowerCase()
                                   + " (" + outcome.addressesAffected() + " address(es) updated)");

        } catch (IllegalArgumentException e) {
            // Malformed payload: acknowledged rather than retried, because a retry sends the same
            // broken body again.
            logger.error("Unreadable SES notification: {}", e.getMessage());
            return ResponseEntity.ok("ignored");
        } catch (Exception e) {
            // Anything else may be transient (a database blip), so let SNS retry.
            logger.error("Failed to process SES notification", e);
            return ResponseEntity.status(500).body("retry");
        }
    }

    /**
     * Rejects notifications claiming to come from a topic we did not subscribe.
     *
     * <p>Skipped when no topic is configured, so the endpoint still works before the ARN is known -
     * the shared secret is the control that always applies.
     */
    private boolean topicAllowed(String body) {
        if (expectedTopicArn.isEmpty()) return true;
        return body.contains(expectedTopicArn);
    }

    /**
     * Completes an SNS subscription by fetching the one-time URL it sent.
     *
     * <p>The host is checked before the call: the URL comes from the request body, and fetching an
     * arbitrary address on request would turn this endpoint into an open proxy.
     */
    /** Whether the callback succeeded, and why not when it did not. */
    private record ConfirmResult(boolean ok, String detail) {}

    private ConfirmResult confirmSubscription(String subscribeUrl) throws Exception {
        if (subscribeUrl == null || subscribeUrl.isBlank()) {
            logger.warn("Subscription confirmation carried no SubscribeURL");
            return new ConfirmResult(false, "no SubscribeURL in the payload");
        }
        URI uri = URI.create(subscribeUrl);
        String host = uri.getHost() == null ? "" : uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !host.endsWith(".amazonaws.com")) {
            logger.warn("Refusing to confirm subscription - SubscribeURL host is not AWS: {}", host);
            return new ConfirmResult(false, "untrusted SubscribeURL host");
        }

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            logger.info("SNS subscription confirmed - HTTP {}", status);
            return new ConfirmResult(true, "confirmed");
        }
        // Reached AWS but it declined: usually an expired or already-used confirmation token.
        logger.warn("SNS subscription confirmation was declined - HTTP {}", status);
        return new ConfirmResult(false, "SNS returned HTTP " + status);
    }

    /** Compares without leaking, through timing, how much of the secret was right. */
    private static boolean constantTimeEquals(String a, String b) {
        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);
        if (x.length != y.length) return false;
        int diff = 0;
        for (int i = 0; i < x.length; i++) diff |= x[i] ^ y[i];
        return diff == 0;
    }
}
