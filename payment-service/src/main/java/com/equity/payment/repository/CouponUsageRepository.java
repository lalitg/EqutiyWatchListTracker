package com.equity.payment.repository;

import com.equity.payment.entity.CouponUsage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponUsageRepository extends JpaRepository<CouponUsage, Long> {

    /** Total uses of this coupon across all users — for max_redemptions check. */
    long countByCouponId(Long couponId);

    /** Uses by a specific user — for max_redemptions_per_user check. */
    long countByCouponIdAndUserId(Long couponId, Long userId);
}
