package com.equity.fundamentals.service;

import com.equity.fundamentals.client.YFinanceWrapperClient;
import com.equity.fundamentals.dto.BalanceSheetDto;
import com.equity.fundamentals.entity.BalanceSheet;
import com.equity.fundamentals.repository.BalanceSheetRepository;
import com.equity.fundamentals.repository.GlobalWatchlistRepository;
import com.equity.fundamentals.util.BatchUtil;
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
import java.util.Map;

/**
 * Fetches annual balance sheets for all companies in global_watchlist and persists them.
 *
 * Flow for the bulk job (fetchForAllCompanies):
 *   1. Split all symbols into chunks (fundamentals.python.chunk-size)
 *   2. Batch-fetch each chunk via YFinanceWrapperClient (one Python process per chunk;
 *      the process itself paces requests to Yahoo — see YFinanceWrapperClient)
 *   3. For each symbol's DTOs: derive the fiscal year label (e.g. FY2024) via
 *      FiscalYearUtil, find-or-create the BalanceSheet row for (symbol, fiscalYear), save
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

    /**
     * Max symbols per yfinance_wrapper.py invocation. See BatchUtil for why this
     * is chunked rather than one process for the whole global_watchlist or one
     * process per symbol.
     */
    @Value("${fundamentals.python.chunk-size:200}")
    private int chunkSize;

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
     *
     * <p>Symbols are split into chunks and fetched with one yfinance_wrapper.py
     * process per chunk — see the class-level doc for why.
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

        for (List<String> chunk : BatchUtil.partition(symbols, chunkSize)) {
            Map<String, List<BalanceSheetDto>> batchResults = apiClient.getBalanceSheetsBatch(chunk);

            for (String symbol : chunk) {
                try {
                    List<BalanceSheetDto> dtos = batchResults.getOrDefault(symbol, Collections.emptyList());
                    if (persistBalanceSheets(symbol, dtos)) success++;
                } catch (Exception e) {
                    log.error("Balance sheet failed for {}: {}", symbol, e.getMessage());
                    failed++;
                }
            }
        }

        long durationSec = (System.currentTimeMillis() - startMs) / 1000;
        log.info("Balance sheet fetch complete — success={} failed={} duration={}s",
                 success, failed, durationSec);
    }

    /**
     * Fetches and saves balance sheets for a single company.
     * Called by the on-demand new-company detection job in FundamentalsScheduler
     * (the bulk path above uses {@link #persistBalanceSheets} directly on
     * already-batch-fetched data instead of calling this).
     *
     * @param symbol NSE symbol e.g. "RELIANCE"
     */
    public void processCompany(String symbol) {
        persistBalanceSheets(symbol, apiClient.getBalanceSheets(symbol));
    }

    /**
     * Persists already-fetched balance-sheet DTOs for one company.
     * Marked @Transactional so all DB writes for one company commit atomically.
     *
     * @return true if at least one year was saved
     */
    @Transactional
    public boolean persistBalanceSheets(String symbol, List<BalanceSheetDto> dtos) {
        if (dtos.isEmpty()) {
            log.warn("No balance sheet data returned for {} — skipping", symbol);
            return false;
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
        return true;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }
}
