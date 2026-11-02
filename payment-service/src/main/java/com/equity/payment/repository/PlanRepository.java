package com.equity.payment.repository;

import com.equity.payment.entity.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanRepository extends JpaRepository<Plan, Long> {

    /** Returns all plans visible on the pricing page. */
    List<Plan> findByActiveTrue();

    /** Used by PlanService.seedPlanToRazorpay() to find plans not yet linked. */
    List<Plan> findByActiveTrueAndRazorpayPlanIdIsNull();

    Optional<Plan> findByName(String name);
}
