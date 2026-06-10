package com.equity.payment.controller;

import com.equity.payment.dto.PlanResponse;
import com.equity.payment.entity.Plan;
import com.equity.payment.service.PlanService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * GET /api/v1/plans
 * GET /api/v1/plans/{id}
 *
 * Both endpoints are PUBLIC — no JWT required.
 * Used by the pricing page to display available plans.
 *
 * Plans are fetched from the DB — marketing can add/deactivate
 * plans without any code change or redeployment.
 */
@RestController
@RequestMapping("/api/v1/plans")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    /**
     * Returns all active plans.
     * Deactivated plans (is_active=false) are excluded.
     */
    @GetMapping
    public ResponseEntity<List<PlanResponse>> getAllPlans() {
        List<PlanResponse> plans = planService.getAllActivePlans().stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(plans);
    }

    /**
     * Returns a single plan by ID.
     * Returns 404 if the plan does not exist or is inactive.
     */
    @GetMapping("/{id}")
    public ResponseEntity<PlanResponse> getPlanById(@PathVariable Long id) {
        Plan plan = planService.getPlanById(id);
        return ResponseEntity.ok(toResponse(plan));
    }

    private PlanResponse toResponse(Plan plan) {
        return new PlanResponse(
                plan.getId(),
                plan.getName(),
                plan.getDisplayName(),
                plan.getDescription(),
                plan.getPricePaise(),
                plan.getBillingCycle().name(),
                plan.getDurationDays()
        );
    }
}
