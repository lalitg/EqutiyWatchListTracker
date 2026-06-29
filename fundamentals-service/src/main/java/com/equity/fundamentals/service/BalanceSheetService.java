package com.equity.fundamentals.service;

import com.equity.fundamentals.client.YFinanceWrapperClient;
import com.equity.fundamentals.dto.BalanceSheetDto;
import com.equity.fundamentals.entity.BalanceSheet;
import com.equity.fundamentals.repository.BalanceSheetRepository;
import com.equity.fundamentals.repository.GlobalWatchlistRepository;
import com.equity.fundamentals.util.FiscalYearUtil;
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
 * Fetches annual balance sheets for all companies in global_watchlist and persists them.
 *
 * Flow for each company:
 *   1. Call YFinanceWrapperClient.getBalanceSheets(symbol) → list of BalanceSheetDto
 *   2. For each DTO, derive the fiscal year label (e.g. FY2024) using FiscalYearUtil
 *   3. Find-or-create the BalanceSheet row for (symbol, fiscalYear)
 *   4. Map DTO fields onto the entity and save
 *   5. Sleep rateLimitDelayMs between companies
 *
 * Data never deleted:
 *   Old fiscal years accumulate in the DB. The API query in main-api-service
 *   applies LIMIT 3 to show only the last 3 years to the frontend.
 *
 * Scoped to global_watchlist:
 *   Only companies tracked in global_watchlist are fetched.
 *   This avoids wasting API quota on all 2200+ NSE companies.
 */
@Service
public class BalanceSheetService {

    private static final Logger log = LoggerFactory.getLogger(BalanceSheetService.class);

    private final GlobalWatchlistRepository globalWatchlistRepo;
    private final BalanceSheetRepository balanceSheetRepo;
    private final YFinanceWrapperClient apiClient;
    private final ObjectMapper objectMapper;

    @Value("${fundamentals.rate-limit.delay-ms:1500}")
    private long rateLimitDelayMs;

    public BalanceSheetService(GlobalWatchlistRepository globalWatchlistRepo,
                               BalanceSheetRepository balanceSheetRepo,
                               YFinanceWrapperClient apiClient,
                               ObjectMapper objectMapper) {
        this.globalWatchlistRepo = globalWatchlistRepo;
        this.balanceSheetRepo    = balanceSheetRepo;
        this.apiClient           = apiClient;
        this.objectMapper        = objectMapper;
    }

    /**
     * Fetches balance sheets for every company currently in global_watchlist.
     * Called by the scheduler on the 1st of every month at 3 AM.
     */
    public void fetchForAllCompanies() {
        List<String> symbols = globalWatchlistRepo.findAllCompanyCodes();

        if (symbols.isEmpty()) {
            log.warn("global_watchlist is empty — balance sheet fetch skipped");
            return;
        }

        Collections.shuffle(symbols);

        int success = 0, failed = 0;
        long startMs = System.currentTimeMillis();

        for (String symbol : symbols) {
            try {
                processCompany(symbol);
                success++;
            } catch (Exception e) {
                log.error("Balance sheet failed for {}: {}", symbol, e.getMessage());
                failed++;
            }

            sleep(rateLimitDelayMs);
        }

        long durationSec = (System.currentTimeMillis() - startMs) / 1000;
        log.info("Balance sheet fetch complete — success={} failed={} duration={}s",
                 success, failed, durationSec);
    }

    /**
     * Fetches and saves balance sheets for a single company.
     * Marked @Transactional so all DB writes for one company commit atomically.
     * Called both by the scheduled fetch (fetchForAllCompanies) and by the
     * on-demand new-company detection job in FundamentalsScheduler.
     *
     * @param symbol NSE symbol e.g. "RELIANCE"
     */
    @Transactional
    public void processCompany(String symbol) {
        List<BalanceSheetDto> dtos = apiClient.getBalanceSheets(symbol);

        if (dtos.isEmpty()) {
            log.warn("No balance sheet data returned for {} — skipping", symbol);
            return;
        }

        for (BalanceSheetDto dto : dtos) {
            if (dto.getPeriodDate() == null) continue;

            String fiscalYear = FiscalYearUtil.toFiscalYearLabel(dto.getPeriodDate());

            // Find existing row or create a new one (upsert via find-or-create)
            BalanceSheet entity = balanceSheetRepo
                .findBySymbolAndFiscalYear(symbol, fiscalYear)
                .orElseGet(BalanceSheet::new);

            entity.setSymbol(symbol);
            entity.setFiscalYear(fiscalYear);
            entity.setPeriodEndDate(dto.getPeriodDate());
            entity.setTotalAssets(dto.getTotalAssets());
            entity.setCurrentAssets(dto.getCurrentAssets());
            entity.setCashAndEquivalents(dto.getCashAndEquivalents());
            entity.setTotalInvestments(dto.getTotalInvestments());
            entity.setFixedAssets(dto.getFixedAssets());
            entity.setTotalLiabilities(dto.getTotalLiabilities());
            entity.setCurrentLiabilities(dto.getCurrentLiabilities());
            entity.setTotalDebt(dto.getTotalDebt());
            entity.setLongTermDebt(dto.getLongTermDebt());
            entity.setShareholdersEquity(dto.getShareholdersEquity());
            entity.setRetainedEarnings(dto.getRetainedEarnings());
            entity.setShareCapital(dto.getShareCapital());
            entity.setDataSource(apiClient.getDataSource());
            entity.setFetchedAt(LocalDateTime.now());
            entity.setRawData(toJson(dto));

            balanceSheetRepo.save(entity);
        }

        log.info("Balance sheets saved for {} ({} years)", symbol, dtos.size());
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
