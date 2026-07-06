package com.equity.fundamentals.service;

import com.equity.fundamentals.client.YFinanceWrapperClient;
import com.equity.fundamentals.dto.QuarterlyResultDto;
import com.equity.fundamentals.entity.QuarterlyResult;
import com.equity.fundamentals.repository.GlobalWatchlistRepository;
import com.equity.fundamentals.repository.QuarterlyResultRepository;
import com.equity.fundamentals.util.QuarterUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Fetches quarterly P&L results for all companies in global_watchlist and persists them.
 *
 * Flow for each company:
 *   1. Call YFinanceWrapperClient.getQuarterlyResults(symbol) → list of QuarterlyResultDto
 *   2. For each DTO, derive the quarter label (e.g. Q2FY25) using QuarterUtil
 *   3. Find-or-create the QuarterlyResult row for (symbol, quarter)
 *   4. Map DTO fields onto the entity and save
 *   5. Sleep rateLimitDelayMs between companies to avoid rate-limiting
 *
 * Upsert pattern:
 *   findBySymbolAndQuarter → orElseGet(QuarterlyResult::new)
 *   This is a "find-or-create" pattern. If the row exists, we update it.
 *   If not, we insert it. No ON CONFLICT SQL needed.
 *
 * Data never deleted:
 *   Old quarters accumulate in the DB. The API query in main-api-service
 *   applies LIMIT 4 to show only the last 4 quarters to the frontend.
 *
 * Scoped to global_watchlist:
 *   Only companies tracked in global_watchlist are fetched.
 *   This avoids wasting API quota on all 2200+ NSE companies.
 */
@Service
public class QuarterlyResultsService {

    private static final Logger log = LoggerFactory.getLogger(QuarterlyResultsService.class);

    private final GlobalWatchlistRepository globalWatchlistRepo;
    private final QuarterlyResultRepository quarterlyResultRepo;
    private final YFinanceWrapperClient apiClient;
    private final ObjectMapper objectMapper;

    @Value("${fundamentals.rate-limit.delay-ms:1500}")
    private long rateLimitDelayMs;

    public QuarterlyResultsService(GlobalWatchlistRepository globalWatchlistRepo,
                                   QuarterlyResultRepository quarterlyResultRepo,
                                   YFinanceWrapperClient apiClient,
                                   ObjectMapper objectMapper) {
        this.globalWatchlistRepo = globalWatchlistRepo;
        this.quarterlyResultRepo = quarterlyResultRepo;
        this.apiClient           = apiClient;
        this.objectMapper        = objectMapper;
    }

    /**
     * Fetches quarterly results for every company currently in global_watchlist.
     * Called by the scheduler — either nightly during results season or weekly off-season.
     */
    public void fetchForAllCompanies() {
        List<String> symbols = globalWatchlistRepo.findAllCompanyCodes();

        if (symbols.isEmpty()) {
            log.warn("global_watchlist is empty — quarterly results fetch skipped");
            return;
        }

        // Shuffle so that if a rate-limit cuts the run short, different companies fail each time
        Collections.shuffle(symbols);

        int success = 0, failed = 0;
        long startMs = System.currentTimeMillis();

        for (String symbol : symbols) {
            try {
                processCompany(symbol);
                success++;
            } catch (Exception e) {
                log.error("Quarterly results failed for {}: {}", symbol, e.getMessage());
                failed++;
            }

            sleep(rateLimitDelayMs);
        }

        long durationSec = (System.currentTimeMillis() - startMs) / 1000;
        log.info("Quarterly results fetch complete — success={} failed={} duration={}s",
                 success, failed, durationSec);
    }

    /**
     * Fetches and saves quarterly results for a single company.
     * Marked @Transactional so all DB writes for one company commit atomically.
     * Called both by the scheduled fetch (fetchForAllCompanies) and by the
     * on-demand new-company detection job in FundamentalsScheduler.
     *
     * @param symbol NSE symbol e.g. "RELIANCE"
     */
    @Transactional
    public void processCompany(String symbol) {
        List<QuarterlyResultDto> dtos = apiClient.getQuarterlyResults(symbol);

        if (dtos.isEmpty()) {
            log.warn("No quarterly data returned for {} — skipping", symbol);
            return;
        }

        for (QuarterlyResultDto dto : dtos) {
            if (dto.getPeriodDate() == null) continue;

            String quarter = QuarterUtil.toQuarterLabel(dto.getPeriodDate());

            // Find existing row or create a new one (upsert via find-or-create)
            QuarterlyResult entity = quarterlyResultRepo
                .findBySymbolAndQuarter(symbol, quarter)
                .orElseGet(QuarterlyResult::new);

            entity.setSymbol(symbol);
            entity.setQuarter(quarter);
            entity.setPeriodEndDate(dto.getPeriodDate());
            entity.setRevenue(dto.getTotalRevenue());
            entity.setGrossProfit(dto.getGrossProfit());
            entity.setOperatingProfit(dto.getEbitda());
            entity.setNetProfit(dto.getNetIncome());
            entity.setEps(dto.getEps());
            entity.setDataSource(apiClient.getDataSource());
            entity.setFetchedAt(LocalDateTime.now());
            entity.setRawData(toJson(dto));

            quarterlyResultRepo.save(entity);
        }

        log.info("Quarterly results saved for {} ({} quarters)", symbol, dtos.size());
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
