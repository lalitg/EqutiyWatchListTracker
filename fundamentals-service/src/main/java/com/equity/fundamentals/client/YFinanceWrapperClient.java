package com.equity.fundamentals.client;

import com.equity.fundamentals.dto.BalanceSheetDto;
import com.equity.fundamentals.dto.ClosingPriceDto;
import com.equity.fundamentals.dto.QuarterlyResultDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs the local yfinance_wrapper.py script (as a short-lived child process, see
 * {@link PythonProcessRunner}) to fetch quarterly results, balance sheets, or
 * closing prices for NSE-listed companies.
 *
 * <p>WHY a Python wrapper: yfinance is a Python library with no Java equivalent.
 *
 * <p>WHY batch methods, not one call per symbol: fetching for the full
 * global_watchlist (2000+ companies) used to mean one HTTP call per symbol per
 * data type against a permanently-running Flask server. That server sat resident
 * in memory 24/7 and was a repeated contributor to EC2 OOM kills, even though the
 * actual work only happens during scheduled fetch windows. The batch methods spawn
 * Python once per chunk of symbols (chunking is the caller's responsibility — see
 * QuarterlyResultsService / BalanceSheetService / PeSnapshotService), so Python's
 * memory is only ever resident for the duration of one chunk.
 *
 * <p>Symbol format for NSE: append ".NS" to the NSE symbol — e.g. RELIANCE →
 * RELIANCE.NS. Callers pass bare NSE symbols; this class handles the suffix.
 *
 * <p>On failure (script crash, timeout, symbol not found, etc.): batch methods
 * return an empty map, or a map missing entries for the symbols that failed —
 * callers should treat a missing key the same as "no data for this symbol."
 */
@Component
public class YFinanceWrapperClient {

    private static final Logger log = LoggerFactory.getLogger(YFinanceWrapperClient.class);

    private static final String DATA_SOURCE = "YFINANCE";
    private static final String NS_SUFFIX = ".NS";

    private final PythonProcessRunner processRunner;
    private final ObjectMapper objectMapper;
    private final String scriptPath;
    private final double delaySeconds;

    public YFinanceWrapperClient(
            PythonProcessRunner processRunner,
            ObjectMapper objectMapper,
            @Value("${python.fundamentals.script:yfinance_wrapper.py}") String scriptPath,
            @Value("${fundamentals.rate-limit.delay-ms:1500}") long rateLimitDelayMs) {
        this.processRunner = processRunner;
        this.objectMapper  = objectMapper;
        this.scriptPath    = scriptPath;
        // Same politeness delay the old design applied between HTTP calls — the
        // script sleeps this long between symbols within a chunk. Reusing the
        // existing property keeps one source of truth instead of two config knobs
        // for the same concept.
        this.delaySeconds  = rateLimitDelayMs / 1000.0;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Batch methods — one Python process per call, covering the whole list
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fetches the last 4 quarters of P&L data for each symbol in one batch.
     *
     * @param nseSymbols bare NSE symbols — e.g. ["RELIANCE", "TCS"]
     * @return map of bare NSE symbol → list of QuarterlyResultDto (up to 4 each);
     *         a symbol with no data maps to an empty list, not omitted
     */
    public Map<String, List<QuarterlyResultDto>> getQuarterlyResultsBatch(List<String> nseSymbols) {
        Map<String, List<QuarterlyResultDto>> result = new LinkedHashMap<>();
        if (nseSymbols.isEmpty()) return result;

        String json = runBatch("quarterly", nseSymbols);
        if (json == null) return result;

        try {
            List<Map<String, Object>> rows = objectMapper.readValue(json, new TypeReference<>() {});
            for (Map<String, Object> row : rows) {
                String symbol = bareSymbol((String) row.get("symbol"));
                Object quartersRaw = row.get("quarters");
                if (quartersRaw == null) {
                    result.put(symbol, Collections.emptyList());
                    continue;
                }
                String quartersJson = objectMapper.writeValueAsString(quartersRaw);
                result.put(symbol, objectMapper.readValue(quartersJson, new TypeReference<List<QuarterlyResultDto>>() {}));
            }
        } catch (Exception e) {
            log.warn("Failed to parse quarterly batch response for {} symbols: {}", nseSymbols.size(), e.getMessage());
        }
        return result;
    }

    /**
     * Fetches the last 3 annual balance sheets for each symbol in one batch.
     *
     * @param nseSymbols bare NSE symbols
     * @return map of bare NSE symbol → list of BalanceSheetDto (up to 3 each)
     */
    public Map<String, List<BalanceSheetDto>> getBalanceSheetsBatch(List<String> nseSymbols) {
        Map<String, List<BalanceSheetDto>> result = new LinkedHashMap<>();
        if (nseSymbols.isEmpty()) return result;

        String json = runBatch("balance-sheet", nseSymbols);
        if (json == null) return result;

        try {
            List<Map<String, Object>> rows = objectMapper.readValue(json, new TypeReference<>() {});
            for (Map<String, Object> row : rows) {
                String symbol = bareSymbol((String) row.get("symbol"));
                Object yearsRaw = row.get("years");
                if (yearsRaw == null) {
                    result.put(symbol, Collections.emptyList());
                    continue;
                }
                String yearsJson = objectMapper.writeValueAsString(yearsRaw);
                result.put(symbol, objectMapper.readValue(yearsJson, new TypeReference<List<BalanceSheetDto>>() {}));
            }
        } catch (Exception e) {
            log.warn("Failed to parse balance-sheet batch response for {} symbols: {}", nseSymbols.size(), e.getMessage());
        }
        return result;
    }

    /**
     * Fetches the previous trading day's closing price for each symbol in one batch.
     *
     * @param nseSymbols bare NSE symbols
     * @return map of bare NSE symbol → ClosingPriceDto; a symbol with no price
     *         available still maps to a DTO with null date/closingPrice, not omitted
     */
    public Map<String, ClosingPriceDto> getClosingPricesBatch(List<String> nseSymbols) {
        Map<String, ClosingPriceDto> result = new LinkedHashMap<>();
        if (nseSymbols.isEmpty()) return result;

        String json = runBatch("closing-price", nseSymbols);
        if (json == null) return result;

        try {
            List<Map<String, Object>> rows = objectMapper.readValue(json, new TypeReference<>() {});
            for (Map<String, Object> row : rows) {
                ClosingPriceDto dto = objectMapper.convertValue(row, ClosingPriceDto.class);
                result.put(bareSymbol(dto.getSymbol()), dto);
            }
        } catch (Exception e) {
            log.warn("Failed to parse closing-price batch response for {} symbols: {}", nseSymbols.size(), e.getMessage());
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Single-symbol convenience wrappers — kept so existing on-demand call sites
    // (FundamentalsScheduler's new-company detection job) need no changes.
    // ─────────────────────────────────────────────────────────────────────────

    public List<QuarterlyResultDto> getQuarterlyResults(String nseSymbol) {
        return getQuarterlyResultsBatch(List.of(nseSymbol)).getOrDefault(nseSymbol, Collections.emptyList());
    }

    public List<BalanceSheetDto> getBalanceSheets(String nseSymbol) {
        return getBalanceSheetsBatch(List.of(nseSymbol)).getOrDefault(nseSymbol, Collections.emptyList());
    }

    public ClosingPriceDto getClosingPrice(String nseSymbol) {
        return getClosingPricesBatch(List.of(nseSymbol)).get(nseSymbol);
    }

    public String getDataSource() {
        return DATA_SOURCE;
    }

    // ─────────────────────────────────────────────────────────────────────────

    private String runBatch(String mode, List<String> nseSymbols) {
        List<String> suffixed = nseSymbols.stream().map(s -> s + NS_SUFFIX).toList();
        String json = processRunner.runBatch(scriptPath, mode, suffixed, delaySeconds);
        if (json == null || json.isBlank()) {
            log.warn("{} batch fetch produced no output for {} symbols", mode, nseSymbols.size());
            return null;
        }
        return json;
    }

    private String bareSymbol(String symbol) {
        if (symbol == null) return null;
        return symbol.endsWith(NS_SUFFIX) ? symbol.substring(0, symbol.length() - NS_SUFFIX.length()) : symbol;
    }
}