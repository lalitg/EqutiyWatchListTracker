package com.companynews.newsscheduler.service;
 
import com.companynews.newsscheduler.repository.CompanySymbolRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
 
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
 
@Service
@RequiredArgsConstructor
@Slf4j
public class CompanySymbolService {
 
    private final CompanySymbolRepository symbolRepository;
 
    // Fallback list used ONLY if the database query fails.
    // This prevents the app from crashing if there is a DB issue.
    @Value("${news.tracked-symbols:}")
    private String fallbackSymbolsRaw;
 
    // This list is loaded once at startup and reused for all fetches.
    private List<String> symbols = Collections.emptyList();
 
    // @PostConstruct: runs automatically after Spring creates this bean.
    // Perfect place to load data that should be ready before any
    // scheduled job runs.
    @PostConstruct
    public void loadSymbols() {
        try {
            symbols = symbolRepository.findAllSymbols();
 
            if (symbols == null || symbols.isEmpty()) {
                log.warn("No symbols found in DB. Using fallback list.");
                symbols = parseFallback();
            } else {
                log.info("Loaded {} company symbols from database.",
                    symbols.size());
            }
        } catch (Exception e) {
            // If the DB query fails for any reason, fall back to the
            // properties list so the app still works.
            log.error("Failed to load symbols from DB: {}. Using fallback.",
                e.getMessage());
            symbols = parseFallback();
        }
    }
 
    // Returns all symbols. Called by GoogleRssService.
    public List<String> getAllSymbols() {
        return symbols;
    }
 
    // Allows manually refreshing the symbol list without restarting.
    // Call POST /api/news/refresh-symbols to trigger this.
    public void refreshSymbols() {
        log.info("Refreshing symbol list from database...");
        loadSymbols();
    }
 
    private List<String> parseFallback() {
        if (fallbackSymbolsRaw == null || fallbackSymbolsRaw.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(fallbackSymbolsRaw.split(","))
               .map(String::trim)
               .filter(s -> !s.isEmpty())
               .collect(Collectors.toList());
    }
}
