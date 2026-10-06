package com.equity.watchlist.service;

import com.equity.watchlist.entity.CompanySubscription;
import com.equity.watchlist.repository.CompanyRepository;
import com.equity.watchlist.repository.CompanySubscriptionRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Manages who gets emailed about which company.
 *
 * <h2>No cap on how many companies a user may follow</h2>
 * Deliberate. The thing worth limiting is how much email someone receives, not how many companies
 * they care about, and that limit lives in the alert engine: one digest per cycle however many
 * companies are in it, and a daily ceiling past which the remainder is grouped hourly rather than
 * dropped. A subscription cap would have constrained the wrong quantity.
 *
 * <h2>Symbols are validated, not trusted</h2>
 * A subscription for a symbol that does not exist would sit in the table forever, matching nothing,
 * and give the user a toggle that silently never fires.
 */
@Service
@Transactional
public class SubscriptionService {

    private static final Logger logger = LogManager.getLogger(SubscriptionService.class);

    private final CompanySubscriptionRepository subscriptionRepository;
    private final CompanyRepository companyRepository;

    public SubscriptionService(CompanySubscriptionRepository subscriptionRepository,
                               CompanyRepository companyRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.companyRepository      = companyRepository;
    }

    /** @return the symbols this user is subscribed to, newest first */
    @Transactional(readOnly = true)
    public List<String> listSymbols(Long userId) {
        return subscriptionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(CompanySubscription::getCompanyCode)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isSubscribed(Long userId, String companyCode) {
        return subscriptionRepository.existsByUserIdAndCompanyCode(userId, normalise(companyCode));
    }

    /**
     * Subscribes a user to a company. Subscribing twice is not an error.
     *
     * @return true if this call created the subscription, false if it already existed
     * @throws IllegalArgumentException the symbol is blank or not a known company
     */
    public boolean subscribe(Long userId, String companyCode) {
        String symbol = normalise(companyCode);
        if (symbol.isEmpty()) {
            throw new IllegalArgumentException("A company symbol is required");
        }
        if (companyRepository.findBySymbol(symbol).isEmpty()) {
            throw new IllegalArgumentException("Unknown company symbol: " + symbol);
        }
        if (subscriptionRepository.existsByUserIdAndCompanyCode(userId, symbol)) {
            return false;
        }

        try {
            subscriptionRepository.save(new CompanySubscription(symbol, userId));
            logger.info("User {} subscribed to alerts for {}", userId, symbol);
            return true;
        } catch (DataIntegrityViolationException e) {
            // Two tabs, or a double-clicked button: the unique constraint did its job and the user
            // already has exactly what they asked for.
            logger.debug("Concurrent subscribe for user {} / {} — already present", userId, symbol);
            return false;
        }
    }

    /**
     * @return true if a subscription was removed, false if there was nothing to remove
     */
    public boolean unsubscribe(Long userId, String companyCode) {
        String symbol = normalise(companyCode);
        long removed = subscriptionRepository.deleteByUserIdAndCompanyCode(userId, symbol);
        if (removed > 0) logger.info("User {} unsubscribed from alerts for {}", userId, symbol);
        return removed > 0;
    }

    /**
     * Removes every subscription for a user — the "turn off all alerts" link in each email.
     *
     * @return how many were removed
     */
    public long unsubscribeAll(Long userId) {
        long removed = subscriptionRepository.deleteByUserId(userId);
        logger.info("User {} turned off all alerts ({} subscription(s) removed)", userId, removed);
        return removed;
    }

    private static String normalise(String companyCode) {
        return companyCode == null ? "" : companyCode.trim().toUpperCase();
    }
}
