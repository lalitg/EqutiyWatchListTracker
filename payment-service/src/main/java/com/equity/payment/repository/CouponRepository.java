package com.equity.payment.repository;

import com.equity.payment.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    /** Lookup by the code string the user typed (case-sensitive). */
    Optional<Coupon> findByCodeAndActiveTrue(String code);
}
