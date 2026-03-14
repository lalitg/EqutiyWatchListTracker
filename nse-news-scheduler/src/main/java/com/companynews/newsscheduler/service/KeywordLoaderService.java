package com.companynews.newsscheduler.service;

import com.companynews.newsscheduler.repository.GlobalWatchlistRepository;
import com.companynews.newsscheduler.repository.SectorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class KeywordLoaderService {

    private static final Logger log = LoggerFactory.getLogger(KeywordLoaderService.class);

    private final GlobalWatchlistRepository watchlistRepository;
    private final SectorRepository sectorRepository;

    public KeywordLoaderService(GlobalWatchlistRepository watchlistRepository,
                                SectorRepository sectorRepository) {
        this.watchlistRepository = watchlistRepository;
        this.sectorRepository = sectorRepository;
    }

    /**
     * Loads all keywords from all 3 sources and returns one combined list.
     *
     * Sources:
     * 1. global_watchlist table  — company symbols
     * 2. sectors table           — sector names
     * 3. keywords.txt file       — additional custom keywords
     *
     * WHY LinkedHashSet:
     * A Set removes duplicates automatically.
     * LinkedHashSet preserves insertion order (so watchlist symbols come first).
     * This prevents fetching the same keyword twice if it appears in multiple sources.
     */
    public List<String> loadAllKeywords() {
        Set<String> keywords = new LinkedHashSet<>();

        // Source 1: Company symbols from global_watchlist table
        try {
            List<String> symbols = watchlistRepository.findAllSymbols();
            keywords.addAll(symbols);
            log.info("Loaded {} symbols from global_watchlist", symbols.size());
        } catch (Exception e) {
            log.error("Failed to load symbols from global_watchlist: {}", e.getMessage());
        }

        // Source 2: Sector names from sectors table
        try {
            List<String> sectors = sectorRepository.findAllSectorNames();
            keywords.addAll(sectors);
            log.info("Loaded {} sectors from sectors table", sectors.size());
        } catch (Exception e) {
            log.error("Failed to load sectors from sectors table: {}", e.getMessage());
        }

        // Source 3: Additional keywords from keywords.txt
        try {
            ClassPathResource resource = new ClassPathResource("keywords.txt");
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream())
            );
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                // Skip empty lines and lines starting with # (comments)
                if (!line.isEmpty() && !line.startsWith("#")) {
                    keywords.add(line);
                    count++;
                }
            }
            reader.close();
            log.info("Loaded {} keywords from keywords.txt", count);
        } catch (Exception e) {
            log.warn("keywords.txt not found or unreadable — skipping: {}", e.getMessage());
        }

        log.info("Total unique keywords to fetch: {}", keywords.size());
        return new ArrayList<>(keywords);
    }
}
