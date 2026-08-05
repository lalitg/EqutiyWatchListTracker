package com.equity.fundamentals.service;

import com.equity.fundamentals.client.YFinanceWrapperClient;
import com.equity.fundamentals.dto.QuarterlyResultDto;
import com.equity.fundamentals.entity.QuarterlyResult;
import com.equity.fundamentals.repository.GlobalWatchlistRepository;
import com.equity.fundamentals.repository.QuarterlyResultRepository;
import com.equity.fundamentals.util.BatchUtil;
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
import java.util.Map;

/**
 * Fetches quarterly P&L results for all companies in global_watchlist and persists them.
 *
 * Flow for the bulk job (fetchForAllCompanies):
 *   1. Split all symbols into chunks (fundamentals.python.chunk-size)
 *   2. Batch-fetch each chunk via YFinanceWrapperClient (one Python process per chunk;
 *      the process itself paces requests to Yahoo — see YFinanceWrapperClient)
 *   3. For each symbol's DTOs: derive the quarter label (e.g. Q2FY25) via QuarterUtil,
 *      find-or-create the QuarterlyResult row for (symbol, quarter), save
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

    /**
     * Max symbols per yfinance_wrapper.py invocation. See BatchUtil for why this
     * is chunked rather than one process for the whole global_watchlist or one
     * process per symbol.
     */
    @Value("${fundamentals.python.chunk-size:200}")
    private int chunkSize;

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
     *
     * <p>Symbols are split into chunks of {@code fundamentals.python.chunk-size} and
     * fetched with one yfinance_wrapper.py process per chunk — not one process for
     * all 2000+ symbols (an unreasonably long single invocation, all-or-nothing on
     * failure) and not one process per symbol (interpreter/import startup cost paid
     * thousands of times). The per-symbol rate-limit delay that used to sit between
     * HTTP calls in this loop now lives inside the script itself, applied between
     * symbols within each chunk — see YFinanceWrapperClient.
     */
    public void fetchForAllCompanies() {
        List<String> symbols = globalWatchlistRepo.findAllCompanyCodes();

        if (symbols.isEmpty()) {
            log.warn("global_watchlist is empty — quarterly results fetch skipped");
            return;
        }

        // Shuffle so that if a run is cut short, different companies are covered each time
        Collections.shuffle(symbols);

        int success = 0, failed = 0;
        long startMs = System.currentTimeMillis();

        for (List<String> chunk : BatchUtil.partition(symbols, chunkSize)) {
            Map<String, List<QuarterlyResultDto>> batchResults = apiClient.getQuarterlyResultsBatch(chunk);

            for (String symbol : chunk) {
                try {
                    List<QuarterlyResultDto> dtos = batchResults.getOrDefault(symbol, Collections.emptyList());
                    if (persistQuarterlyResults(symbol, dtos)) success++;
                } catch (Exception e) {
                    log.error("Quarterly results failed for {}: {}", symbol, e.getMessage());
                    failed++;
                }
            }
        }

        long durationSec = (System.currentTimeMillis() - startMs) / 1000;
        log.info("Quarterly results fetch complete — success={} failed={} duration={}s",
                 success, failed, durationSec);
    }

    /**
     * Fetches and saves quarterly results for a single company.
     * Called by the on-demand new-company detection job in FundamentalsScheduler
     * (the bulk path above uses {@link #persistQuarterlyResults} directly on
     * already-batch-fetched data instead of calling this).
     *
     * @param symbol NSE symbol e.g. "RELIANCE"
     */
    public void processCompany(String symbol) {
        persistQuarterlyResults(symbol, apiClient.getQuarterlyResults(symbol));
    }

    /**
     * Persists already-fetched quarterly DTOs for one company.
     * Marked @Transactional so all DB writes for one company commit atomically.
     *
     * @return true if at least one quarter was saved
     */
    @Transactional
    public boolean persistQuarterlyResults(String symbol, List<QuarterlyResultDto> dtos) {
        if (dtos.isEmpty()) {
            log.warn("No quarterly data returned for {} — skipping", symbol);
            return false;
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
