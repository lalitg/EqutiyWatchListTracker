package com.equity.fastmovers.service;

import com.equity.fastmovers.client.NseMoversClient;
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
import java.time.DayOfWeek;
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
    // Persist daily close prices — called at 9:30 AM to fetch previous trading day
    // -------------------------------------------------------------------------

    public void persistDailyClose() {
        // Walk back up to 7 weekdays to find the most recent trading day with a published Bhavcopy.
        // This handles public holidays (no Bhavcopy for those dates → 404) automatically.
        LocalDate candidate = LocalDate.now();
        Map<String, BigDecimal> bhavcopy = Map.of();
        for (int i = 0; i < 7; i++) {
            candidate = previousTradingDay(candidate);
            bhavcopy = nseMoversClient.fetchBhavcopy(candidate);
            if (!bhavcopy.isEmpty()) break;
            logger.info("persistDailyClose: no Bhavcopy for {} (holiday?), trying earlier date", candidate);
        }

        if (bhavcopy.isEmpty()) {
            logger.warn("persistDailyClose: no Bhavcopy found in last 7 weekdays — skipping");
            return;
        }

        LocalDate priceDate = candidate;
        logger.info("persistDailyClose: persisting {} close prices for {}", bhavcopy.size(), priceDate);

        int inserted = 0, skipped = 0;
        for (Map.Entry<String, BigDecimal> entry : bhavcopy.entrySet()) {
            String symbol    = entry.getKey();
            BigDecimal close = entry.getValue();
            if (dailyPriceRepository.findByCompanyCodeAndPriceDate(symbol, priceDate).isPresent()) {
                skipped++;
                continue;
            }
            DailyPrice dp = new DailyPrice();
            dp.setCompanyCode(symbol);
            dp.setPriceDate(priceDate);
            dp.setClosePrice(close);
            dailyPriceRepository.save(dp);
            inserted++;
        }

        logger.info("persistDailyClose complete for {} — inserted={}, skipped={} out of {} symbols",
                priceDate, inserted, skipped, bhavcopy.size());
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

    private LocalDate previousTradingDate() {
        return previousTradingDay(LocalDate.now());
    }

    /** Returns the most recent weekday before the given date, skipping Saturday and Sunday. */
    private LocalDate previousTradingDay(LocalDate date) {
        LocalDate d = date.minusDays(1);
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
            d = d.minusDays(1);
        }
        return d;
    }
}
