package com.equity.fundamentals.scheduler;

import com.equity.fundamentals.repository.GlobalWatchlistRepository;
import com.equity.fundamentals.repository.QuarterlyResultRepository;
import com.equity.fundamentals.service.BalanceSheetService;
import com.equity.fundamentals.service.QuarterlyResultsService;
import com.equity.fundamentals.service.ResultsSeasonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Orchestrates all fundamentals fetch jobs.
 *
 * Four scheduled jobs:
 *
 *   1. scheduledQuarterlyFetch()  — runs nightly at 11 PM.
 *      Checks if today is within a results season (isResultsSeason()).
 *      If YES → fetches quarterly results for all companies in global_watchlist.
 *      If NO  → skips to avoid useless API calls off-season.
 *
 *   2. weeklyOffSeasonSync()  — runs every Sunday at 2 AM.
 *      Runs year-round but skips when results season is already active
 *      (to avoid double-fetching). Catches amendments and restatements
 *      published outside the main results window.
 *
 *   3. scheduledBalanceSheetFetch()  — runs on the 1st of every month at 3 AM.
 *      Fetches annual balance sheets for all companies in global_watchlist.
 *      Runs year-round; balance sheets only change once a year (after Q4 results).
 *
 *   4. detectAndFetchNewCompanies()  — runs every 5 minutes.
 *      Compares global_watchlist against quarterly_results.
 *      Any symbol present in global_watchlist but missing from quarterly_results
 *      is a newly added company — fetches both quarterly results and balance sheets
 *      for it immediately (on-demand fetch).
 *      Once a company has data, it won't be re-triggered by this job.
 *
 * Startup behaviour:
 *   On first boot, no fetch is triggered automatically. The scheduled jobs
 *   will run at their configured times. To do a manual one-time backfill,
 *   call triggerManualFetch() directly (see comment below).
 *
 * AtomicBoolean startupDone:
 *   Guards against duplicate startup execution if ApplicationReadyEvent
 *   fires more than once (can happen in some Spring contexts).
 */
@Component
public class FundamentalsScheduler {

    private static final Logger log = LoggerFactory.getLogger(FundamentalsScheduler.class);

    private final QuarterlyResultsService quarterlyResultsService;
    private final BalanceSheetService balanceSheetService;
    private final ResultsSeasonUtil resultsSeasonUtil;
    private final GlobalWatchlistRepository globalWatchlistRepo;
    private final QuarterlyResultRepository quarterlyResultRepo;

    private final AtomicBoolean startupDone = new AtomicBoolean(false);

    public FundamentalsScheduler(QuarterlyResultsService quarterlyResultsService,
                                  BalanceSheetService balanceSheetService,
                                  ResultsSeasonUtil resultsSeasonUtil,
                                  GlobalWatchlistRepository globalWatchlistRepo,
                                  QuarterlyResultRepository quarterlyResultRepo) {
        this.quarterlyResultsService = quarterlyResultsService;
        this.balanceSheetService     = balanceSheetService;
        this.resultsSeasonUtil       = resultsSeasonUtil;
        this.globalWatchlistRepo     = globalWatchlistRepo;
        this.quarterlyResultRepo     = quarterlyResultRepo;
    }

    /**
     * Fires once after the application is fully started (all beans ready, DB connected).
     * Uses ApplicationReadyEvent — fires exactly once, unlike ContextRefreshedEvent.
     *
     * Logs a startup confirmation. Does NOT trigger a fetch on startup to avoid
     * hammering the yfinance API every time the service restarts.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (!startupDone.compareAndSet(false, true)) return;
        log.info("=== fundamentals-service started — quarterly and balance-sheet jobs scheduled ===");
        log.info("Results season active: {}", resultsSeasonUtil.isResultsSeason());
        log.info("New-company detection job polls every 5 minutes");
    }

    /**
     * Nightly at 11 PM — fetches quarterly results if in results season.
     *
     * Results season windows (SEBI 45-day rule):
     *   Q1: Jul 15 – Aug 31
     *   Q2: Oct 15 – Nov 30
     *   Q3: Jan 15 – Feb 28
     *   Q4: May 01 – Jun 30
     *
     * Off-season: job fires but exits immediately after logging.
     * The weekly Sunday job handles off-season syncs.
     */
    @Scheduled(cron = "${fundamentals.quarterly.cron:0 0 23 * * *}")
    public void scheduledQuarterlyFetch() {
        if (!resultsSeasonUtil.isResultsSeason()) {
            log.info("Quarterly results — off-season, skipping nightly fetch");
            return;
        }
        log.info("Quarterly results — results season active, starting fetch");
        quarterlyResultsService.fetchForAllCompanies();
    }

    /**
     * Every Sunday at 2 AM — off-season weekly sync for quarterly results.
     *
     * Runs year-round but skips when results season is active
     * (nightly job already covers it). Catches amended filings published
     * outside the main 45-day window.
     */
    @Scheduled(cron = "${fundamentals.weekly-sync.cron:0 0 2 * * SUN}")
    public void weeklyOffSeasonSync() {
        if (resultsSeasonUtil.isResultsSeason()) {
            log.info("Weekly sync — results season active, nightly job covers this. Skipping.");
            return;
        }
        log.info("Weekly off-season sync — fetching quarterly results");
        quarterlyResultsService.fetchForAllCompanies();
    }

    /**
     * 1st of every month at 3 AM — fetches annual balance sheets.
     *
     * Balance sheets are published once a year after Q4 results (typically June–Sept).
     * Running monthly is more than enough — it catches the annual report when it arrives
     * and is otherwise a no-op (data doesn't change month-to-month).
     */
    @Scheduled(cron = "${fundamentals.balance-sheet.cron:0 0 3 1 * *}")
    public void scheduledBalanceSheetFetch() {
        log.info("Balance sheet fetch starting");
        balanceSheetService.fetchForAllCompanies();
    }

    /**
     * Runs every 5 minutes — detects newly added companies and fetches their data immediately.
     *
     * How it works:
     *   1. Load all company codes from global_watchlist.
     *   2. Load all distinct symbols that already have quarterly data in quarterly_results.
     *   3. Any symbol in global_watchlist but NOT in quarterly_results is new.
     *   4. Fetch both quarterly results AND balance sheets for each new symbol right away.
     *
     * Why quarterly_results as the reference (not balance_sheets)?
     *   Both tables are populated together in this job. Checking quarterly_results
     *   is sufficient — a symbol absent from it is guaranteed to have no fundamentals data.
     *
     * Why fixedDelay instead of a cron?
     *   fixedDelay waits 5 minutes after the previous execution finishes.
     *   This prevents overlap if a previous run was still processing.
     */
    @Scheduled(fixedDelayString = "${fundamentals.new-company-poll.delay-ms:300000}")
    public void detectAndFetchNewCompanies() {
        List<String> watchlistSymbols = globalWatchlistRepo.findAllCompanyCodes();
        if (watchlistSymbols.isEmpty()) return;

        List<String> existingSymbols = quarterlyResultRepo.findDistinctSymbols();
        Set<String> existingSet = new HashSet<>(existingSymbols);

        List<String> newSymbols = watchlistSymbols.stream()
            .filter(s -> !existingSet.contains(s))
            .collect(Collectors.toList());

        if (newSymbols.isEmpty()) return;

        log.info("New companies detected in global_watchlist (no fundamentals data yet): {}", newSymbols);

        for (String symbol : newSymbols) {
            try {
                log.info("On-demand fetch starting for new company: {}", symbol);
                quarterlyResultsService.processCompany(symbol);
                balanceSheetService.processCompany(symbol);
                log.info("On-demand fetch complete for: {}", symbol);
            } catch (Exception e) {
                log.error("On-demand fetch failed for {}: {}", symbol, e.getMessage());
            }
        }
    }
}
