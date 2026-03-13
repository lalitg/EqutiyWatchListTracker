package com.equity.watchlist.service;

import com.equity.watchlist.dto.WatchlistRequest;
import com.equity.watchlist.dto.WatchlistView;
import com.equity.watchlist.entity.Watchlist;
import com.equity.watchlist.repository.CompanyRepository;
import com.equity.watchlist.repository.WatchlistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service layer handling business logic for watchlist operations.
 */
@Service
public class WatchlistService {

    private static final Logger logger = LoggerFactory.getLogger(WatchlistService.class);
    private static final Long DEFAULT_USER_ID = 1L;

    private final WatchlistRepository watchlistRepository;
    private final CompanyRepository companyRepository;

    public WatchlistService(WatchlistRepository watchlistRepository, CompanyRepository companyRepository) {
        this.watchlistRepository = watchlistRepository;
        this.companyRepository = companyRepository;
    }

    /**
     * Adds a new company to the watchlist.
     */
    public WatchlistView addCompany(WatchlistRequest request) {
        if (request.getCompanyCode() == null || request.getCompanyCode().isBlank()) {
            throw new IllegalArgumentException("Company code is required");
        }

        Watchlist entity = new Watchlist();
        entity.setUserId(DEFAULT_USER_ID);
        entity.setCompanyCode(request.getCompanyCode().toUpperCase().trim());
        entity.setWeek52Low(request.getWeek52Low());
        entity.setWeek52High(request.getWeek52High());
        entity.setAllTimeLow(request.getAllTimeLow());
        entity.setAllTimeHigh(request.getAllTimeHigh());
        entity.setCurrentValue(request.getCurrentValue());
        entity.setTradedVolume(request.getTradedVolume());

        Watchlist saved = watchlistRepository.save(entity);
        logger.info("Added company '{}' to watchlist", saved.getCompanyCode());
        return toView(saved);
    }

    /**
     * Returns all watchlist entries ordered by creation time (oldest first).
     */
    public List<WatchlistView> getWatchlist() {
        return watchlistRepository.findByUserIdOrderByCreatedAtAsc(DEFAULT_USER_ID)
                .stream()
                .map(this::toView)
                .collect(Collectors.toList());
    }

    /**
     * Updates an existing watchlist entry identified by company code.
     */
    public WatchlistView updateCompany(String companyCode, WatchlistRequest request) {
        Watchlist entity = watchlistRepository
                .findByUserIdAndCompanyCode(DEFAULT_USER_ID, companyCode)
                .orElseThrow(() -> new IllegalArgumentException("Company not found: " + companyCode));

        if (request.getCompanyCode() != null && !request.getCompanyCode().isBlank()) {
            entity.setCompanyCode(request.getCompanyCode().toUpperCase().trim());
        }
        entity.setWeek52Low(request.getWeek52Low());
        entity.setWeek52High(request.getWeek52High());
        entity.setAllTimeLow(request.getAllTimeLow());
        entity.setAllTimeHigh(request.getAllTimeHigh());
        entity.setCurrentValue(request.getCurrentValue());
        entity.setTradedVolume(request.getTradedVolume());

        Watchlist updated = watchlistRepository.save(entity);
        logger.info("Updated company '{}' in watchlist", updated.getCompanyCode());
        return toView(updated);
    }

    /**
     * Removes a company from the watchlist.
     */
    @Transactional
    public void removeCompany(String companyCode) {
        watchlistRepository.deleteByUserIdAndCompanyCode(DEFAULT_USER_ID, companyCode);
        logger.info("Removed company '{}' from watchlist", companyCode);
    }

    /**
     * Returns the count of entries in the watchlist.
     */
    public long getCount() {
        return watchlistRepository.countByUserId(DEFAULT_USER_ID);
    }

    /**
     * Converts a Watchlist entity to a WatchlistView DTO.
     * Looks up company name from company_master if available.
     */
    private WatchlistView toView(Watchlist entity) {
        WatchlistView view = new WatchlistView();
        view.setCompanyCode(entity.getCompanyCode());

        // Populate company name from company_master
        companyRepository.findBySymbol(entity.getCompanyCode())
                .ifPresent(cm -> view.setCompanyName(cm.getCompanyName()));

        view.setWeek52Low(entity.getWeek52Low());
        view.setWeek52High(entity.getWeek52High());
        view.setAllTimeLow(entity.getAllTimeLow());
        view.setAllTimeHigh(entity.getAllTimeHigh());
        view.setCurrentValue(entity.getCurrentValue());
        view.setTradedVolume(entity.getTradedVolume());
        return view;
    }
}
