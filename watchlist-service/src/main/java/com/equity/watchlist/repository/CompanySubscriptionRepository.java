package com.equity.watchlist.repository;

import com.equity.watchlist.entity.CompanySubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link CompanySubscription}.
 *
 * <p>The alert engine does not use this repository: it runs in the other JVM and reads the same
 * table with plain SQL, the way {@code CompanyNameLookup} already reads {@code company_master}.
 * Everything here serves the user-facing side — subscribing, listing, unsubscribing.
 */
public interface CompanySubscriptionRepository extends JpaRepository<CompanySubscription, Long> {

    /** Every company this user has subscribed to, newest first. */
    List<CompanySubscription> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** One specific subscription, used to answer "is this company already subscribed?". */
    Optional<CompanySubscription> findByUserIdAndCompanyCode(Long userId, String companyCode);

    boolean existsByUserIdAndCompanyCode(Long userId, String companyCode);

    /** Removes one subscription. Returns how many rows went, so a no-op is distinguishable. */
    long deleteByUserIdAndCompanyCode(Long userId, String companyCode);

    /** Removes every subscription for a user — the "turn off all alerts" link in each email. */
    long deleteByUserId(Long userId);

    long countByUserId(Long userId);
}
