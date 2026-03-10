package com.watchlist.global.service;

import com.watchlist.global.entity.GlobalWatchlist;
import com.watchlist.global.repository.CompanyRepository;
import com.watchlist.global.repository.GlobalWatchlistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GlobalWatchlistService {

    private static final Logger logger = LoggerFactory.getLogger(GlobalWatchlistService.class);

    private final GlobalWatchlistRepository globalWatchlistRepository;
    private final CompanyRepository companyRepository;

    public GlobalWatchlistService(
            GlobalWatchlistRepository globalWatchlistRepository,
            CompanyRepository companyRepository) {

        this.globalWatchlistRepository = globalWatchlistRepository;
        this.companyRepository         = companyRepository;
    }

    public boolean addSymbol(String symbol) {
        if (globalWatchlistRepository.existsBySymbol(symbol)) {
            logger.info("Symbol {} already in global watchlist", symbol);
            return false;
        }

        return companyRepository.findBySymbol(symbol).map(company -> {
            globalWatchlistRepository.save(new GlobalWatchlist(company.getId(), symbol));
            logger.info("Added {} to global watchlist", symbol);
            return true;
        }).orElseGet(() -> {
            logger.warn("Symbol {} not found in company_master", symbol);
            return false;
        });
    }
}
