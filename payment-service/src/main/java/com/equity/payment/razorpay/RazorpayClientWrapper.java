package com.equity.payment.razorpay;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Wraps all Razorpay SDK calls.
 *
 * All direct Razorpay API interactions are isolated here.
 * Services call this wrapper — they never use the Razorpay SDK directly.
 * This makes mocking easy in tests and isolates SDK version changes.
 *
 * Razorpay test mode:
 *   Set RAZORPAY_KEY_ID=rzp_test_XXXXXXXX and RAZORPAY_KEY_SECRET=your_test_secret.
 *   Get test keys from: https://dashboard.razorpay.com → Settings → API Keys → Test Mode.
 *
 * Razorpay Subscriptions API flow:
 *   1. createRazorpayPlan()      → "plan_XXXXXX"   (once per our plan row)
 *   2. createSubscription()      → "sub_YYYYYY"    (once per user subscribing)
 *   3. verifyPaymentSignature()  → boolean         (on /verify call)
 *   4. cancelSubscription()      → void            (on user cancellation)
 *   5. verifyWebhookSignature()  → boolean         (on every webhook)
 */
@Component
public class RazorpayClientWrapper {

    private static final Logger logger = LoggerFactory.getLogger(RazorpayClientWrapper.class);

    private final String keyId;
    private final String keySecret;
    private final String webhookSecret;

    public RazorpayClientWrapper(
            @Value("${razorpay.key.id}") String keyId,
            @Value("${razorpay.key.secret}") String keySecret,
            @Value("${razorpay.webhook.secret}") String webhookSecret) {
        this.keyId         = keyId;
        this.keySecret     = keySecret;
        this.webhookSecret = webhookSecret;
    }

    /**
     * Creates a Razorpay Plan.
     * Called ONCE per plan row in our plans table (by PlanService.seedPlanToRazorpay).
     * Returns the Razorpay plan ID: "plan_XXXXXX".
     *
     * Razorpay Plan API: POST https://api.razorpay.com/v1/plans
     */
    public String createRazorpayPlan(int pricePaise, String interval, String name) {
        try {
            RazorpayClient client = new RazorpayClient(keyId, keySecret);

            JSONObject planRequest = new JSONObject();
            planRequest.put("period", interval.toLowerCase());   // "monthly" or "yearly"
            planRequest.put("interval", 1);                       // every 1 period
            planRequest.put("item", new JSONObject()
                    .put("name", name)
                    .put("amount", pricePaise)
                    .put("unit_amount", pricePaise)
                    .put("currency", "INR"));

            com.razorpay.Plan razorpayPlan = client.plans.create(planRequest);
            String razorpayPlanId = razorpayPlan.get("id");
            logger.info("Razorpay plan created: {} for our plan: {}", razorpayPlanId, name);
            return razorpayPlanId;

        } catch (RazorpayException e) {
            logger.error("Failed to create Razorpay plan for {}: {}", name, e.getMessage());
            throw new RuntimeException("Razorpay plan creation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a Razorpay Subscription linked to a Razorpay Plan.
     * Called when a user subscribes. Returns Razorpay subscription ID: "sub_YYYYYY".
     * total_count=0 means the subscription repeats indefinitely until cancelled.
     *
     * Razorpay Subscription API: POST https://api.razorpay.com/v1/subscriptions
     */
    public String createSubscription(String razorpayPlanId) {
        try {
            RazorpayClient client = new RazorpayClient(keyId, keySecret);

            JSONObject subscriptionRequest = new JSONObject();
            subscriptionRequest.put("plan_id", razorpayPlanId);
            subscriptionRequest.put("total_count", 0);     // 0 = infinite recurring
            subscriptionRequest.put("quantity", 1);

            com.razorpay.Subscription subscription = client.subscriptions.create(subscriptionRequest);
            String subscriptionId = subscription.get("id");
            logger.info("Razorpay subscription created: {}", subscriptionId);
            return subscriptionId;

        } catch (RazorpayException e) {
            logger.error("Failed to create Razorpay subscription: {}", e.getMessage());
            throw new RuntimeException("Razorpay subscription creation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Cancels a Razorpay Subscription at the end of the current billing cycle.
     * Called when user cancels via POST /api/v1/subscriptions/cancel.
     * After this, Razorpay will not charge the user again after the current period.
     *
     * Razorpay Cancel API: POST https://api.razorpay.com/v1/subscriptions/{id}/cancel
     */
    public void cancelSubscription(String razorpaySubscriptionId) {
        try {
            RazorpayClient client = new RazorpayClient(keyId, keySecret);

            JSONObject cancelRequest = new JSONObject();
            cancelRequest.put("cancel_at_cycle_end", 1);   // 1 = cancel at end of billing cycle

            client.subscriptions.cancel(razorpaySubscriptionId, cancelRequest);
            logger.info("Razorpay subscription cancelled: {}", razorpaySubscriptionId);

        } catch (RazorpayException e) {
            logger.error("Failed to cancel Razorpay subscription {}: {}", razorpaySubscriptionId, e.getMessage());
            throw new RuntimeException("Razorpay subscription cancellation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Verifies the HMAC-SHA256 signature for a subscription payment.
     *
     * Razorpay computes:
     *   HMAC-SHA256(razorpayPaymentId + "|" + razorpaySubscriptionId, keySecret)
     *
     * Called in SubscriptionService.verifyAndActivate() to confirm the first payment.
     * Returns true if the signature is valid — safe to activate subscription.
     * Returns false if tampered — reject with 400.
     */
    public boolean verifyPaymentSignature(String razorpayPaymentId,
                                          String razorpaySubscriptionId,
                                          String razorpaySignature) {
        try {
            JSONObject options = new JSONObject();
            options.put("razorpay_payment_id", razorpayPaymentId);
            options.put("razorpay_subscription_id", razorpaySubscriptionId);
            options.put("razorpay_signature", razorpaySignature);

            return Utils.verifyPaymentSignature(options, keySecret);

        } catch (RazorpayException e) {
            logger.warn("Payment signature verification failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Verifies the HMAC-SHA256 signature on incoming Razorpay webhooks.
     *
     * Razorpay computes:
     *   HMAC-SHA256(rawRequestBody, webhookSecret)
     * and sends it in the X-Razorpay-Signature header.
     *
     * Called in WebhookController before routing to WebhookHandler.
     * Returns false if the header is missing or the signature doesn't match.
     * Any invalid webhook is rejected with HTTP 400 — never processed.
     */
    public boolean verifyWebhookSignature(String rawPayload, String razorpaySignature) {
        try {
            return Utils.verifyWebhookSignature(rawPayload, razorpaySignature, webhookSecret);
        } catch (RazorpayException e) {
            logger.warn("Webhook signature verification failed: {}", e.getMessage());
            return false;
        }
    }

    public String getKeyId() {
        return keyId;
    }
}
