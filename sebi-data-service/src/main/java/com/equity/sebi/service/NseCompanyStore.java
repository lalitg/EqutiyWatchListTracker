package com.equity.sebi.service;

import com.equity.sebi.model.MatchTier;
import com.equity.sebi.model.NseCompany;
import com.equity.sebi.model.NseMatchResult;
import jakarta.annotation.PostConstruct;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.*;

/**
 * Loads the NSE equity list CSV on startup and exposes a match() method
 * that runs the 3-tier name matching against all ~2200 listed companies.
 *
 * CSV from: https://nsearchives.nseindia.com/content/equities/EQUITY_L.csv
 * Columns:  SYMBOL(0), NAME OF COMPANY(1), SERIES(2), ..., ISIN NUMBER(6)
 */
@Service
public class NseCompanyStore {

    private static final Logger log = LogManager.getLogger(NseCompanyStore.class);

    @Value("${nse.equity.csv.url}")
    private String csvUrl;

    private final RestTemplate restTemplate;
    private final NameMatchingService matcher;

    // normalizedName → NseCompany
    private final Map<String, NseCompany> byNormalizedName = new LinkedHashMap<>();
    // NSE symbol → NseCompany (for direct lookups)
    private final Map<String, NseCompany> bySymbol = new HashMap<>();

    public NseCompanyStore(RestTemplate restTemplate, NameMatchingService matcher) {
        this.restTemplate = restTemplate;
        this.matcher = matcher;
    }

    @PostConstruct
    public void load() {
        log.info("Loading NSE equity list from {}", csvUrl);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent",
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            headers.set("Referer", "https://www.nseindia.com/");
            headers.set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");

            ResponseEntity<String> response = restTemplate.exchange(
                    csvUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            parseCsv(response.getBody());
            log.info("NSE company store loaded: {} companies", byNormalizedName.size());
        } catch (Exception e) {
            log.error("Failed to load NSE equity CSV: {}", e.getMessage());
        }
    }

    private void parseCsv(String csv) {
        if (csv == null || csv.isBlank()) return;
        try (BufferedReader reader = new BufferedReader(new StringReader(csv))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine) { firstLine = false; continue; } // skip header
                String[] cols = line.split(",", -1);
                if (cols.length < 7) continue;

                String symbol = cols[0].trim();
                String name   = cols[1].trim();
                String isin   = cols[6].trim();

                if (symbol.isBlank() || name.isBlank()) continue;

                String normalized = matcher.normalize(name);
                if (normalized.isBlank()) continue;

                NseCompany company = new NseCompany(symbol, name, isin);
                byNormalizedName.put(normalized, company);
                bySymbol.put(symbol.toUpperCase(), company);
            }
        } catch (Exception e) {
            log.error("CSV parse error: {}", e.getMessage());
        }
    }

    /**
     * Match a raw candidate name against all NSE companies.
     * Returns the best match with tier info, or empty if no match found.
     */
    public Optional<NseMatchResult> match(String candidateName) {
        if (candidateName == null || candidateName.isBlank()) return Optional.empty();

        String norm = matcher.normalize(candidateName);

        // Tier 1 — exact normalized match
        NseCompany exact = byNormalizedName.get(norm);
        if (exact != null) return Optional.of(new NseMatchResult(exact, MatchTier.EXACT));

        // Tier 2 & 3 — delegate to NameMatchingService
        Optional<String> bestKey = matcher.findBestMatch(candidateName, byNormalizedName.keySet());
        if (bestKey.isPresent()) {
            NseCompany company = byNormalizedName.get(bestKey.get());
            MatchTier tier = determineTier(norm, bestKey.get());
            return Optional.of(new NseMatchResult(company, tier));
        }

        return Optional.empty();
    }

    /**
     * Look up a company directly by NSE symbol.
     */
    public Optional<NseCompany> findBySymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) return Optional.empty();
        return Optional.ofNullable(bySymbol.get(symbol.toUpperCase()));
    }

    private MatchTier determineTier(String norm, String matchedKey) {
        if (norm.equals(matchedKey)) return MatchTier.EXACT;
        String longer  = norm.length() >= matchedKey.length() ? norm : matchedKey;
        String shorter = norm.length() < matchedKey.length()  ? norm : matchedKey;
        if (longer.contains(shorter)) return MatchTier.SUBSTRING;
        return MatchTier.TOKEN_JACCARD;
    }

    public int size() {
        return byNormalizedName.size();
    }

    public boolean isLoaded() {
        return !byNormalizedName.isEmpty();
    }
}
