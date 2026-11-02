package com.equity.payment.controller;

import com.equity.payment.razorpay.RazorpayClientWrapper;
import com.equity.payment.service.WebhookHandler;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/v1/webhooks/razorpay
 *
 * PUBLIC endpoint — no JWT. Razorpay calls this asynchronously.
 * Security is via X-Razorpay-Signature HMAC-SHA256 header verification.
 *
 * This controller:
 *   1. Reads the raw request body (as String — required for signature verification)
 *   2. Verifies the X-Razorpay-Signature header
 *   3. Parses the JSON event
 *   4. Routes to WebhookHandler.handle()
 *   5. Always returns 200 to Razorpay (even for unknown events)
 *      Razorpay retries if it doesn't receive 200 — so we must always ack.
 *
 * Configure webhook URL in Razorpay Dashboard:
 *   Test:  https://your-ngrok-url/api/v1/webhooks/razorpay
 *   Prod:  https://api.niveshflow.com/api/v1/webhooks/razorpay
 */
@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    private static final Logger logger = LoggerFactory.getLogger(WebhookController.class);

    private final RazorpayClientWrapper razorpayClient;
    private final WebhookHandler        webhookHandler;

    public WebhookController(RazorpayClientWrapper razorpayClient, WebhookHandler webhookHandler) {
        this.razorpayClient = razorpayClient;
        this.webhookHandler = webhookHandler;
    }

    @PostMapping("/razorpay")
    public ResponseEntity<Void> handleRazorpayWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {

        // Step 1 — verify signature (reject fakes)
        if (signature == null || !razorpayClient.verifyWebhookSignature(rawPayload, signature)) {
            logger.warn("Invalid Razorpay webhook signature — rejected");
            return ResponseEntity.badRequest().build();
        }

        // Step 2 — parse event
        try {
            JSONObject body  = new JSONObject(rawPayload);
            String     event = body.getString("event");
            JSONObject payload = body.optJSONObject("payload");

            // Step 3 — route to handler
            webhookHandler.handle(event, payload != null ? payload : new JSONObject());

        } catch (Exception e) {
            // Log error but still return 200 — prevents Razorpay from infinite retries
            logger.error("Error processing webhook: {}", e.getMessage(), e);
        }

        // Step 4 — always return 200 to Razorpay
        return ResponseEntity.ok().build();
    }
}
