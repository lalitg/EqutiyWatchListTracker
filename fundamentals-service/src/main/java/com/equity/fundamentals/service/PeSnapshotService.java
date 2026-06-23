package com.equity.fundamentals.service;

import com.equity.fundamentals.client.YFinanceWrapperClient;
import com.equity.fundamentals.dto.ClosingPriceDto;
import com.equity.fundamentals.entity.CompanyPeSnapshot;
import com.equity.fundamentals.entity.QuarterlyResult;
import com.equity.fundamentals.repository.CompanyPeSnapshotRepository;
import com.equity.fundamentals.repository.GlobalWatchlistRepository;
import com.equity.fundamentals.repository.QuarterlyResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Calculates and persists daily P/E ratio snapshots for all companies in global_watchlist.
 *
 * Called by FundamentalsScheduler.dailyPeSnapshotJob() every weekday at 4:30 PM IST,
 * after NSE market close (3:30 PM IST).
 *
 * For each company:
 *   1. Query quarterly_results for the last 4 quarters' EPS → sum = TTM EPS
 *   2. Call yfinance wrapper GET /closing-price → previous trading day's close
 *   3. trailing_pe = closing_price / ttm_eps
 *   4. Upsert a row in company_pe_snapshot for (symbol, trade_date)
 *
 * P/E is stored as null (not calculated) when:
 *   - No EPS data exists for the company yet
 *   - TTM EPS is zero (would produce infinity)
 *   - Closing price could not be fetched
 *
 * Negative P/E is stored as-is when TTM EPS < 0 (loss-making company).
 * The consumer (frontend/API) should display "N/A" for null or negative values.
 */
@Service
public class PeSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(PeSnapshotService.class);

    private final GlobalWatchlistRepository globalWatchlistRepo;
    private final QuarterlyResultRepository quarterlyResultRepo;
    private final CompanyPeSnapshotRepository peSnapshotRepo;
    private final YFinanceWrapperClient apiClient;

    @Value("${fundamentals.rate-limit.delay-ms:1500}")
    private long rateLimitDelayMs;

    public PeSnapshotService(GlobalWatchlistRepository globalWatchlistRepo,
                             QuarterlyResultRepository quarterlyResultRepo,
                             CompanyPeSnapshotRepository peSnapshotRepo,
                             YFinanceWrapperClient apiClient) {
        this.globalWatchlistRepo = globalWatchlistRepo;
        this.quarterlyResultRepo = quarterlyResultRepo;
        this.peSnapshotRepo      = peSnapshotRepo;
        this.apiClient           = apiClient;
    }

    /**
     * Processes all companies in global_watchlist.
     * Called by the daily scheduler job.
     */
    public void processAllCompanies() {
        List<String> symbols = globalWatchlistRepo.findAllCompanyCodes();

        if (symbols.isEmpty()) {
            log.warn("global_watchlist is empty — P/E snapshot skipped");
            return;
        }

        int success = 0, failed = 0, skipped = 0;
        long startMs = System.currentTimeMillis();

        for (String symbol : symbols) {
            try {
                boolean processed = processCompany(symbol);
                if (processed) success++; else skipped++;
            } catch (Exception e) {
                log.error("P/E snapshot failed for {}: {}", symbol, e.getMessage());
                failed++;
            }
            sleep(rateLimitDelayMs);
        }

        long durationSec = (System.currentTimeMillis() - startMs) / 1000;
        log.info("P/E snapshot complete — success={} skipped={} failed={} duration={}s",
                 success, skipped, failed, durationSec);
    }

    /**
     * Calculates and saves the P/E snapshot for a single company.
     *
     * @param symbol NSE symbol e.g. "RELIANCE"
     * @return true if a snapshot was saved, false if skipped (no EPS or no price data)
     */
    @Transactional
    public boolean processCompany(String symbol) {

        // Step 1 — Calculate TTM EPS from last 4 quarters stored in DB
        List<QuarterlyResult> last4 = quarterlyResultRepo
                .findTop4BySymbolOrderByPeriodEndDateDesc(symbol);

        List<BigDecimal> epsList = last4.stream()
                .filter(q -> q.getEps() != null)
                .map(QuarterlyResult::getEps)
                .toList();

        if (epsList.isEmpty()) {
            log.debug("No EPS data for {} — P/E snapshot skipped", symbol);
            return false;
        }

        BigDecimal ttmEps = epsList.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int quartersUsed = epsList.size();

        // Step 2 — Fetch previous trading day's closing price
        ClosingPriceDto priceDto = apiClient.getClosingPrice(symbol);

        if (priceDto == null || priceDto.getClosingPrice() == null || priceDto.getDate() == null) {
            log.warn("No closing price for {} — P/E snapshot skipped", symbol);
            return false;
        }

        // Step 3 — Calculate trailing P/E
        // Null when TTM EPS is zero to avoid division by zero / infinite P/E
        BigDecimal trailingPe = null;
        if (ttmEps.compareTo(BigDecimal.ZERO) != 0) {
            trailingPe = priceDto.getClosingPrice()
                    .divide(ttmEps, 4, RoundingMode.HALF_UP);
        }

        // Step 4 — Upsert: update existing row for same (symbol, date) or insert new
        CompanyPeSnapshot snapshot = peSnapshotRepo
                .findBySymbolAndPeDate(symbol, priceDto.getDate())
                .orElseGet(CompanyPeSnapshot::new);

        snapshot.setSymbol(symbol);
        snapshot.setPeDate(priceDto.getDate());
        snapshot.setClosingPrice(priceDto.getClosingPrice());
        snapshot.setTtmEps(ttmEps);
        snapshot.setTrailingPe(trailingPe);
        snapshot.setQuartersUsed(quartersUsed);
        snapshot.setFetchedAt(LocalDateTime.now());

        peSnapshotRepo.save(snapshot);

        log.info("P/E snapshot saved for {} — date={} price={} ttmEps={} ({}Q) pe={}",
                 symbol, priceDto.getDate(), priceDto.getClosingPrice(),
                 ttmEps, quartersUsed, trailingPe);
        return true;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
