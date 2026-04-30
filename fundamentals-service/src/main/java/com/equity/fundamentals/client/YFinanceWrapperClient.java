package com.equity.fundamentals.client;

import com.equity.fundamentals.dto.BalanceSheetDto;
import com.equity.fundamentals.dto.QuarterlyResultDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * HTTP client that calls the local Python yfinance Flask wrapper.
 *
 * The Python wrapper (yfinance_wrapper.py) runs on localhost:5001 and exposes:
 *   GET /quarterly?symbol=RELIANCE.NS   → list of up to 4 quarters of P&L data
 *   GET /balance-sheet?symbol=RELIANCE.NS → list of up to 3 years of balance sheet data
 *
 * Why a Python wrapper?
 *   yfinance is a Python library with no Java equivalent. Rather than using a
 *   subprocess call or JNI, we run a thin Flask REST API that the Java service
 *   calls over HTTP. This keeps the Java code clean and decoupled from Python.
 *
 * Symbol format for NSE:
 *   Append ".NS" to the NSE symbol — e.g. RELIANCE → RELIANCE.NS
 *   yfinance uses Yahoo Finance's format for Indian stocks.
 *
 * On failure (Python wrapper down, symbol not found, etc.):
 *   Returns empty list. The caller logs it as FAILED in fundamentals_fetch_log.
 */
@Component
public class YFinanceWrapperClient {

    private static final Logger log = LoggerFactory.getLogger(YFinanceWrapperClient.class);

    private static final String DATA_SOURCE = "YFINANCE";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public YFinanceWrapperClient(RestTemplate restTemplate,
                                  ObjectMapper objectMapper,
                                  @Value("${fundamentals.yfinance.base-url:http://localhost:5001}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
    }

    /**
     * Fetches the last 4 quarters of P&L data for the given NSE symbol.
     *
     * Calls: GET {baseUrl}/quarterly?symbol={symbol}.NS
     *
     * @param nseSymbol NSE symbol without suffix — e.g. "RELIANCE"
     * @return list of up to 4 QuarterlyResultDto; empty list on any error
     */
    public List<QuarterlyResultDto> getQuarterlyResults(String nseSymbol) {
        String url = baseUrl + "/quarterly?symbol=" + nseSymbol + ".NS";
        try {
            String json = restTemplate.getForObject(url, String.class);
            if (json == null || json.isBlank()) return Collections.emptyList();

            Map<String, Object> response = objectMapper.readValue(json, new TypeReference<>() {});
            Object quartersRaw = response.get("quarters");
            if (quartersRaw == null) return Collections.emptyList();

            String quartersJson = objectMapper.writeValueAsString(quartersRaw);
            return objectMapper.readValue(quartersJson, new TypeReference<List<QuarterlyResultDto>>() {});

        } catch (Exception e) {
            log.warn("yfinance quarterly fetch failed for {}: {}", nseSymbol, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Fetches the last 3 annual balance sheets for the given NSE symbol.
     *
     * Calls: GET {baseUrl}/balance-sheet?symbol={symbol}.NS
     *
     * @param nseSymbol NSE symbol without suffix — e.g. "RELIANCE"
     * @return list of up to 3 BalanceSheetDto; empty list on any error
     */
    public List<BalanceSheetDto> getBalanceSheets(String nseSymbol) {
        String url = baseUrl + "/balance-sheet?symbol=" + nseSymbol + ".NS";
        try {
            String json = restTemplate.getForObject(url, String.class);
            if (json == null || json.isBlank()) return Collections.emptyList();

            Map<String, Object> response = objectMapper.readValue(json, new TypeReference<>() {});
            Object yearsRaw = response.get("years");
            if (yearsRaw == null) return Collections.emptyList();

            String yearsJson = objectMapper.writeValueAsString(yearsRaw);
            return objectMapper.readValue(yearsJson, new TypeReference<List<BalanceSheetDto>>() {});

        } catch (Exception e) {
            log.warn("yfinance balance-sheet fetch failed for {}: {}", nseSymbol, e.getMessage());
            return Collections.emptyList();
        }
    }

    public String getDataSource() {
        return DATA_SOURCE;
    }
}
