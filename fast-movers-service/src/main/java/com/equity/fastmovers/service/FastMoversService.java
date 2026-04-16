package com.equity.fastmovers.service;

import com.equity.fastmovers.client.NseMoversClient;
import com.equity.fastmovers.client.NseMoversClient.HistoricalClose;
import com.equity.fastmovers.client.NseMoversClient.MoverData;
import com.equity.fastmovers.dto.FastMoverEntry;
import com.equity.fastmovers.dto.FastMoversResponse;
import com.equity.fastmovers.entity.CompanyMaster;
import com.equity.fastmovers.entity.DailyPrice;
import com.equity.fastmovers.entity.FastMoversCache;
import com.equity.fastmovers.repository.CompanyMasterRepository;
import com.equity.fastmovers.repository.DailyPriceRepository;
import com.equity.fastmovers.repository.FastMoversCacheRepository;
import jakarta.annotation.PostConstruct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class FastMoversService {

    private static final Logger logger = LogManager.getLogger(FastMoversService.class);
    private static final int TOP_N = 10;

    private final FastMoversCacheRepository cacheRepository;
    private final DailyPriceRepository dailyPriceRepository;
    private final CompanyMasterRepository companyMasterRepository;
    private final NseMoversClient nseMoversClient;

    public FastMoversService(FastMoversCacheRepository cacheRepository,
                             DailyPriceRepository dailyPriceRepository,
                             CompanyMasterRepository companyMasterRepository,
                             NseMoversClient nseMoversClient) {
        this.cacheRepository        = cacheRepository;
        this.dailyPriceRepository   = dailyPriceRepository;
        this.companyMasterRepository = companyMasterRepository;
        this.nseMoversClient        = nseMoversClient;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns top gainers and losers for the given range.
     * range values: TODAY, 1D, 2D, 3D, 4D, 1W
     */
    public FastMoversResponse getMovers(String range) {
        logger.info("getMovers called for range={}", range);
        return switch (range.toUpperCase()) {
            case "TODAY" -> fromCache(LocalDate.now(), range);
            case "1D"    -> fromCache(previousTradingDate(), range);
            case "2D"    -> fromDailyPrice(3, range);
            case "3D"    -> fromDailyPrice(4, range);
            case "4D"    -> fromDailyPrice(5, range);
            case "1W"    -> fromDailyPrice(6, range);
            default      -> throw new IllegalArgumentException("Invalid range: " + range + ". Valid: TODAY, 1D, 2D, 3D, 4D, 1W");
        };
    }

    // -------------------------------------------------------------------------
    // Refresh today's movers — called hourly and on startup
    // -------------------------------------------------------------------------

    public void refreshTodayMovers() {
        logger.info("refreshTodayMovers: fetching gainers/losers from NSE");
        try {
            String cookies = nseMoversClient.fetchSessionCookies();
            List<MoverData> gainers = nseMoversClient.fetchGainers(cookies);
            List<MoverData> losers  = nseMoversClient.fetchLosers(cookies);

            LocalDate today = LocalDate.now();
            LocalDateTime now = LocalDateTime.now();

            upsertMovers(gainers, "GAINER", today, now);
            upsertMovers(losers,  "LOSER",  today, now);

            logger.info("refreshTodayMovers: upserted {} gainers, {} losers for {}",
                    gainers.size(), losers.size(), today);
        } catch (Exception e) {
            logger.error("refreshTodayMovers failed: {}", e.getMessage(), e);
        }
    }

    private void upsertMovers(List<MoverData> movers, String type, LocalDate priceDate, LocalDateTime fetchedAt) {
        for (MoverData m : movers) {
            String companyName = resolveCompanyName(m.getSymbol(), m.getCompanyName());
            cacheRepository.upsert(
                    priceDate, type, m.getRank(),
                    m.getSymbol(), companyName,
                    m.getCurrentPrice(), m.getPctChange(),
                    fetchedAt
            );
        }
    }

    // -------------------------------------------------------------------------
    // Persist daily close prices — called at 3:30 PM
    // -------------------------------------------------------------------------

    public void persistDailyClose() {
        logger.info("persistDailyClose: starting final refresh then close price persist");

        // 1. Lock in today's final result in the cache
        refreshTodayMovers();

        // 2. Fetch close prices for all active companies
        LocalDate today = LocalDate.now();
        List<CompanyMaster> companies = companyMasterRepository.findByActiveTrueOrderBySymbolAsc();
        logger.info("persistDailyClose: fetching close prices for {} companies", companies.size());

        String cookies = nseMoversClient.fetchSessionCookies();
        int inserted = 0, skipped = 0, failed = 0;

        for (int i = 0; i < companies.size(); i++) {
            CompanyMaster company = companies.get(i);
            String symbol = company.getSymbol();

            // Skip if already persisted for today (idempotent re-run safety)
            if (dailyPriceRepository.findByCompanyCodeAndPriceDate(symbol, today).isPresent()) {
                skipped++;
                continue;
            }

            try {
                List<HistoricalClose> history = nseMoversClient.fetchHistoricalClose(
                        symbol, today, today, cookies);

                if (history.isEmpty()) {
                    logger.debug("persistDailyClose: no data for '{}' on {} — skipping", symbol, today);
                    skipped++;
                    continue;
                }

                BigDecimal closePrice = history.get(0).getClosePrice();
                DailyPrice dp = new DailyPrice();
                dp.setCompanyCode(symbol);
                dp.setPriceDate(today);
                dp.setClosePrice(closePrice);
                dailyPriceRepository.save(dp);
                inserted++;

            } catch (Exception e) {
                logger.debug("persistDailyClose: failed for '{}': {}", symbol, e.getMessage());
                failed++;
            }

            if ((i + 1) % 100 == 0) {
                logger.info("persistDailyClose: progress {}/{} — inserted={}, skipped={}, failed={}",
                        i + 1, companies.size(), inserted, skipped, failed);
                // Refresh cookies every 100 companies to avoid session expiry
                cookies = nseMoversClient.fetchSessionCookies();
            }
        }

        logger.info("persistDailyClose complete — inserted={}, skipped={}, failed={} out of {} companies",
                inserted, skipped, failed, companies.size());
    }

    // -------------------------------------------------------------------------
    // Startup backfill
    // -------------------------------------------------------------------------

    @PostConstruct
    public void onStartup() {
        new Thread(this::refreshTodayMovers, "startup-refresh-thread").start();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private FastMoversResponse fromCache(LocalDate priceDate, String range) {
        List<FastMoversCache> gainers = cacheRepository
                .findByPriceDateAndTypeOrderByRankAsc(priceDate, "GAINER");
        List<FastMoversCache> losers  = cacheRepository
                .findByPriceDateAndTypeOrderByRankAsc(priceDate, "LOSER");

        return new FastMoversResponse(
                range,
                gainers.stream().map(this::cacheToEntry).collect(Collectors.toList()),
                losers.stream().map(this::cacheToEntry).collect(Collectors.toList()),
                LocalDateTime.now()
        );
    }

    private FastMoversResponse fromDailyPrice(int compareRank, String range) {
        List<Object[]> gainerRows = dailyPriceRepository.findTopGainers(compareRank, TOP_N);
        List<Object[]> loserRows  = dailyPriceRepository.findTopLosers(compareRank, TOP_N);

        Map<String, String> nameMap = companyMasterRepository.findByActiveTrueOrderBySymbolAsc()
                .stream()
                .collect(Collectors.toMap(CompanyMaster::getSymbol, c -> c.getCompanyName() != null ? c.getCompanyName() : "", (a, b) -> a));

        List<FastMoverEntry> gainers = toEntries(gainerRows, nameMap);
        List<FastMoverEntry> losers  = toEntries(loserRows, nameMap);

        return new FastMoversResponse(range, gainers, losers, LocalDateTime.now());
    }

    private List<FastMoverEntry> toEntries(List<Object[]> rows, Map<String, String> nameMap) {
        List<FastMoverEntry> entries = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Object[] row = rows.get(i);
            String code         = (String) row[0];
            BigDecimal current  = toBigDecimal(row[1]);
            BigDecimal base     = toBigDecimal(row[2]);
            BigDecimal pct      = toBigDecimal(row[3]);
            String name         = nameMap.getOrDefault(code, "");
            entries.add(new FastMoverEntry(i + 1, code, name, current, base,
                    pct != null ? pct.setScale(2, RoundingMode.HALF_UP) : null));
        }
        return entries;
    }

    private FastMoverEntry cacheToEntry(FastMoversCache c) {
        return new FastMoverEntry(
                c.getRank(), c.getCompanyCode(), c.getCompanyName(),
                c.getCurrentPrice(), null, c.getPctChange()
        );
    }

    private BigDecimal toBigDecimal(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal bd) return bd;
        return new BigDecimal(o.toString());
    }

    private String resolveCompanyName(String symbol, String fallback) {
        Optional<CompanyMaster> cm = companyMasterRepository.findBySymbol(symbol);
        if (cm.isPresent() && cm.get().getCompanyName() != null) return cm.get().getCompanyName();
        return fallback != null ? fallback : "";
    }

    /**
     * Returns the most recent date before today that has data in daily_price.
     * Falls back to yesterday if no data exists yet.
     */
    private LocalDate previousTradingDate() {
        // For 1D, just return yesterday — the 3:30 PM scheduler saves yesterday's cache row
        return LocalDate.now().minusDays(1);
    }
}
