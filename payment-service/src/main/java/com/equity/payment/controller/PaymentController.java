package com.equity.payment.controller;

import com.equity.payment.entity.Payment;
import com.equity.payment.repository.PaymentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * GET /api/v1/payments/history
 *
 * Returns the authenticated user's paginated payment history.
 * Shows both FIRST_PAYMENT and RENEWAL rows — full audit trail.
 * JWT required.
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentRepository paymentRepository;

    public PaymentController(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @GetMapping("/history")
    public ResponseEntity<Page<Map<String, Object>>> getHistory(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size) {

        Long userId = (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        Page<Map<String, Object>> history = paymentRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
                .map(this::toMap);

        return ResponseEntity.ok(history);
    }

    private Map<String, Object> toMap(Payment p) {
        return Map.of(
                "id",                   p.getId(),
                "paymentType",          p.getPaymentType().name(),
                "originalAmountPaise",  p.getOriginalAmountPaise(),
                "discountPaise",        p.getDiscountPaise(),
                "finalAmountPaise",     p.getFinalAmountPaise(),
                "status",               p.getStatus().name(),
                "razorpayPaymentId",    p.getRazorpayPaymentId() != null ? p.getRazorpayPaymentId() : "",
                "createdAt",            p.getCreatedAt().toString()
        );
    }
}
