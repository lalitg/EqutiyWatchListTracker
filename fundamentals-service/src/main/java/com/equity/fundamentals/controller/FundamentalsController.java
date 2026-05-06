package com.equity.fundamentals.controller;

import com.equity.fundamentals.entity.BalanceSheet;
import com.equity.fundamentals.entity.CompanyPeSnapshot;
import com.equity.fundamentals.entity.QuarterlyResult;
import com.equity.fundamentals.repository.BalanceSheetRepository;
import com.equity.fundamentals.repository.CompanyPeSnapshotRepository;
import com.equity.fundamentals.repository.QuarterlyResultRepository;
import com.equity.fundamentals.service.BalanceSheetService;
import com.equity.fundamentals.service.QuarterlyResultsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * REST controller exposing fundamentals data for the company detail page.
 *
 * All endpoints are prefixed with /api/fundamentals.
 * Data is served from the DB (populated by FundamentalsScheduler).
 * On a cache miss (no DB rows yet) the controller triggers an on-demand
 * fetch from the yfinance wrapper so the first page-load returns real data.
 */
@RestController
@RequestMapping("/api/fundamentals")
public class FundamentalsController {

    private static final Logger log = LoggerFactory.getLogger(FundamentalsController.class);

    private final QuarterlyResultRepository quarterlyRepo;
    private final BalanceSheetRepository    balanceSheetRepo;
    private final CompanyPeSnapshotRepository peRepo;
    private final QuarterlyResultsService   quarterlyService;
    private final BalanceSheetService       balanceSheetService;

    public FundamentalsController(QuarterlyResultRepository quarterlyRepo,
                                  BalanceSheetRepository balanceSheetRepo,
                                  CompanyPeSnapshotRepository peRepo,
                                  QuarterlyResultsService quarterlyService,
                                  BalanceSheetService balanceSheetService) {
        this.quarterlyRepo      = quarterlyRepo;
        this.balanceSheetRepo   = balanceSheetRepo;
        this.peRepo             = peRepo;
        this.quarterlyService   = quarterlyService;
        this.balanceSheetService = balanceSheetService;
    }

    /**
     * Returns the last 4 quarters of P&L data for the given NSE symbol.
     * Triggers an on-demand fetch if no data exists yet.
     */
    @GetMapping("/{symbol}/quarterly-results")
    public ResponseEntity<List<Map<String, Object>>> getQuarterlyResults(@PathVariable String symbol) {
        String sym = symbol.toUpperCase();
        log.info("GET /api/fundamentals/{}/quarterly-results", sym);

        List<QuarterlyResult> rows = quarterlyRepo.findTop4BySymbolOrderByPeriodEndDateDesc(sym);
        if (rows.isEmpty()) {
            log.info("No quarterly data for {} — fetching on-demand", sym);
            try {
                quarterlyService.processCompany(sym);
                rows = quarterlyRepo.findTop4BySymbolOrderByPeriodEndDateDesc(sym);
            } catch (Exception e) {
                log.warn("On-demand quarterly fetch failed for {}: {}", sym, e.getMessage());
            }
        }

        List<Map<String, Object>> result = rows.stream()
            .map(q -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("symbol",          q.getSymbol());
                m.put("quarter",         q.getQuarter());
                m.put("periodEndDate",   q.getPeriodEndDate());
                m.put("revenue",         q.getRevenue());
                m.put("grossProfit",     q.getGrossProfit());
                m.put("operatingProfit", q.getOperatingProfit());
                m.put("netProfit",       q.getNetProfit());
                m.put("eps",             q.getEps());
                m.put("revenueGrowthYoy", q.getRevenueGrowthYoy());
                m.put("profitGrowthYoy", q.getProfitGrowthYoy());
                return m;
            })
            .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    /**
     * Returns the last 3 annual balance sheets for the given NSE symbol.
     * Triggers an on-demand fetch if no data exists yet.
     */
    @GetMapping("/{symbol}/balance-sheet")
    public ResponseEntity<List<Map<String, Object>>> getBalanceSheet(@PathVariable String symbol) {
        String sym = symbol.toUpperCase();
        log.info("GET /api/fundamentals/{}/balance-sheet", sym);

        List<BalanceSheet> rows = balanceSheetRepo.findTop3BySymbolOrderByPeriodEndDateDesc(sym);
        if (rows.isEmpty()) {
            log.info("No balance sheet data for {} — fetching on-demand", sym);
            try {
                balanceSheetService.processCompany(sym);
                rows = balanceSheetRepo.findTop3BySymbolOrderByPeriodEndDateDesc(sym);
            } catch (Exception e) {
                log.warn("On-demand balance sheet fetch failed for {}: {}", sym, e.getMessage());
            }
        }

        List<Map<String, Object>> result = rows.stream()
            .map(b -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("symbol",              b.getSymbol());
                m.put("fiscalYear",          b.getFiscalYear());
                m.put("periodEndDate",       b.getPeriodEndDate());
                m.put("totalAssets",         b.getTotalAssets());
                m.put("currentAssets",       b.getCurrentAssets());
                m.put("cashAndEquivalents",  b.getCashAndEquivalents());
                m.put("totalInvestments",    b.getTotalInvestments());
                m.put("fixedAssets",         b.getFixedAssets());
                m.put("totalLiabilities",    b.getTotalLiabilities());
                m.put("currentLiabilities",  b.getCurrentLiabilities());
                m.put("totalDebt",           b.getTotalDebt());
                m.put("longTermDebt",        b.getLongTermDebt());
                m.put("shareholdersEquity",  b.getShareholdersEquity());
                m.put("retainedEarnings",    b.getRetainedEarnings());
                m.put("shareCapital",        b.getShareCapital());
                return m;
            })
            .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    /**
     * Returns the latest trailing P/E snapshot for the given NSE symbol.
     */
    @GetMapping("/{symbol}/pe")
    public ResponseEntity<Map<String, Object>> getPeSnapshot(@PathVariable String symbol) {
        String sym = symbol.toUpperCase();
        log.info("GET /api/fundamentals/{}/pe", sym);

        Optional<CompanyPeSnapshot> snap = peRepo.findFirstBySymbolOrderByPeDateDesc(sym);
        if (snap.isEmpty()) {
            return ResponseEntity.ok(Map.of("symbol", sym, "trailingPe", (Object) null, "ttmEps", (Object) null));
        }

        CompanyPeSnapshot s = snap.get();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("symbol",       s.getSymbol());
        m.put("peDate",       s.getPeDate());
        m.put("closingPrice", s.getClosingPrice());
        m.put("ttmEps",       s.getTtmEps());
        m.put("trailingPe",   s.getTrailingPe());
        m.put("quartersUsed", s.getQuartersUsed());
        return ResponseEntity.ok(m);
    }
}
