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
     *
     * <p>total_count is how many billing cycles the mandate covers, and Razorpay requires at
     * least 1 — an open-ended subscription is not something the API accepts, whatever we would
     * prefer. ("The total count must be at least 1.") So the caller passes a count far beyond any
     * real subscription's life: the customer cancels, or their card expires, long before it runs
     * out, and if one ever did reach the end Razorpay simply stops charging rather than
     * overcharging.
     *
     * Razorpay Subscription API: POST https://api.razorpay.com/v1/subscriptions
     *
     * @param razorpayPlanId the plan this subscription bills against
     * @param totalCount     number of billing cycles to authorise; must be at least 1
     */
    public String createSubscription(String razorpayPlanId, int totalCount) {
        try {
            RazorpayClient client = new RazorpayClient(keyId, keySecret);

            JSONObject subscriptionRequest = new JSONObject();
            subscriptionRequest.put("plan_id", razorpayPlanId);
            subscriptionRequest.put("total_count", Math.max(1, totalCount));
            subscriptionRequest.put("quantity", 1);
            // Razorpay's own emails about the mandate: we send our own messages, and two sets of
            // mail about the same payment is how a reader learns to ignore both.
            subscriptionRequest.put("customer_notify", 0);

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
        cancelSubscription(razorpaySubscriptionId, false);
    }

    /**
     * Cancels a Razorpay subscription, now or at the end of the cycle.
     *
     * <p>A customer cancelling keeps what they paid for, so that cancels at cycle end. A mandate
     * being replaced — a plan switch, or an attempt that was abandoned — must stop immediately,
     * or the customer is charged twice a month for one product.
     *
     * @param immediately true to stop the mandate now, false to let the paid cycle finish
     */
    public void cancelSubscription(String razorpaySubscriptionId, boolean immediately) {
        try {
            RazorpayClient client = new RazorpayClient(keyId, keySecret);

            JSONObject cancelRequest = new JSONObject();
            cancelRequest.put("cancel_at_cycle_end", immediately ? 0 : 1);

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
     * <p>Razorpay signs a subscription payment over
     * {@code razorpayPaymentId + "|" + razorpaySubscriptionId}, and a one-off order payment over
     * {@code razorpayOrderId + "|" + razorpayPaymentId}. They are different messages, and the SDK
     * has a different helper for each: {@code Utils.verifySubscription} and
     * {@code Utils.verifyPaymentSignature}. Using the order helper here — as this did — made every
     * subscription verification throw {@code JSONObject["razorpay_order_id"] not found}, which
     * surfaced as a 500 and meant no purchase could ever be confirmed.
     *
     * <p>Called by SubscriptionService.verifyAndActivate() to confirm the first payment.
     *
     * @return true when the signature is genuine and the subscription may be activated; false for
     *         anything else, which the caller turns into a 400 with nothing marked as paid
     */
    public boolean verifyPaymentSignature(String razorpayPaymentId,
                                          String razorpaySubscriptionId,
                                          String razorpaySignature) {
        try {
            JSONObject options = new JSONObject();
            options.put("razorpay_payment_id", razorpayPaymentId);
            options.put("razorpay_subscription_id", razorpaySubscriptionId);
            options.put("razorpay_signature", razorpaySignature);

            return Utils.verifySubscription(options, keySecret);

        } catch (Exception e) {
            // Deliberately broad. A missing field makes the SDK throw a JSONException rather than
            // a RazorpayException, and an unverified payment must always come back as "no", never
            // as a server error that leaves the caller guessing whether they were charged.
            logger.warn("Subscription payment signature rejected: {}", e.getMessage());
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
