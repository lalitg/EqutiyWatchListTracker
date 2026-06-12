package com.equity.payment.service;

import com.equity.payment.entity.Plan;
import com.equity.payment.exception.PlanNotFoundException;
import com.equity.payment.razorpay.RazorpayClientWrapper;
import com.equity.payment.repository.PlanRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Manages plan listing and Razorpay plan synchronisation.
 *
 * Plans are DB-driven. No plan name, price, or duration is hardcoded in Java.
 * The marketing team inserts/updates plan rows directly in the DB.
 *
 * seedPlanToRazorpay(): runs at startup via @PostConstruct.
 * For every active plan in our DB that has no razorpay_plan_id yet,
 * it calls the Razorpay Plans API and stores the returned ID.
 * This is idempotent — plans that already have a razorpay_plan_id are skipped.
 */
@Service
@Transactional
public class PlanService {

    private static final Logger logger = LoggerFactory.getLogger(PlanService.class);

    private final PlanRepository planRepository;
    private final RazorpayClientWrapper razorpayClient;

    public PlanService(PlanRepository planRepository, RazorpayClientWrapper razorpayClient) {
        this.planRepository = planRepository;
        this.razorpayClient = razorpayClient;
    }

    /**
     * Returns all active plans — used by GET /api/v1/plans.
     * Plans with is_active=false are not returned (stop-selling scenario).
     */
    @Transactional(readOnly = true)
    public List<Plan> getAllActivePlans() {
        return planRepository.findByActiveTrue();
    }

    /**
     * Returns a single active plan by ID — used by GET /api/v1/plans/{id}.
     * Throws PlanNotFoundException (→ 404) if not found or inactive.
     */
    @Transactional(readOnly = true)
    public Plan getPlanById(Long planId) {
        return planRepository.findById(planId)
                .filter(Plan::isActive)
                .orElseThrow(() -> new PlanNotFoundException("Plan not found: " + planId));
    }

    /**
     * Called at startup. For each active plan without a razorpay_plan_id,
     * creates the plan in Razorpay and saves the returned ID to our DB.
     *
     * Why at startup?
     * Plan rows are inserted via SQL (not through our API).
     * When a new plan row is added, the next service restart picks it up
     * and creates the corresponding Razorpay plan automatically.
     *
     * If Razorpay is unreachable (e.g. no API keys in dev), the error is
     * logged as a warning and the service still starts successfully.
     * Plans without razorpay_plan_id cannot be used for subscriptions
     * until the service restarts with working Razorpay credentials.
     */
    @PostConstruct
    public void seedPlanToRazorpay() {
        List<Plan> unlinkedPlans = planRepository.findByActiveTrueAndRazorpayPlanIdIsNull();
        if (unlinkedPlans.isEmpty()) {
            logger.info("All active plans are already linked to Razorpay.");
            return;
        }

        for (Plan plan : unlinkedPlans) {
            try {
                String billingInterval = plan.getBillingCycle().name(); // MONTHLY or ANNUAL
                String rzpPlanId = razorpayClient.createRazorpayPlan(
                        plan.getPricePaise(),
                        billingInterval,
                        plan.getDisplayName()
                );
                plan.setRazorpayPlanId(rzpPlanId);
                planRepository.save(plan);
                logger.info("Plan '{}' linked to Razorpay plan: {}", plan.getName(), rzpPlanId);
            } catch (Exception e) {
                // Don't fail startup — just log the warning
                logger.warn("Could not create Razorpay plan for '{}': {}. " +
                        "Service starts anyway. Subscriptions for this plan will not work " +
                        "until Razorpay is reachable and service is restarted.", plan.getName(), e.getMessage());
            }
        }
    }
}
