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
public class KeywordLoader {

    private static final Logger log = LoggerFactory.getLogger(KeywordLoader.class);

    private final GlobalWatchlistRepository watchlistRepository;
    private final SectorRepository sectorRepository;

    public KeywordLoader(GlobalWatchlistRepository watchlistRepository,
                         SectorRepository sectorRepository) {
        this.watchlistRepository = watchlistRepository;
        this.sectorRepository = sectorRepository;
    }

    /**
     * Loads all keywords from three sources and returns a deduplicated ordered list.
     *
     * Sources (in priority order):
     *  1. global_watchlist table — company symbols (e.g. INFY, RELIANCE)
     *  2. sector_companies table — sector names (e.g. Banking, Pharma)
     *  3. keywords.txt classpath file — custom/macro keywords (e.g. Nifty 50, RBI)
     *
     * WHY LinkedHashSet:
     * A plain HashSet removes duplicates but loses insertion order.
     * LinkedHashSet preserves insertion order while still removing duplicates.
     * Watchlist symbols always appear first — they are the highest-priority keywords.
     *
     * Each source is wrapped in a try-catch so a DB outage or missing file
     * does not prevent the other sources from loading.
     */
    public List<String> load() {
        Set<String> keywords = new LinkedHashSet<>();

        try {
            List<String> symbols = watchlistRepository.findAllSymbols();
            keywords.addAll(symbols);
            log.info("Loaded {} symbols from global_watchlist", symbols.size());
        } catch (Exception e) {
            log.error("Failed to load symbols from global_watchlist: {}", e.getMessage());
        }

        try {
            List<String> sectors = sectorRepository.findAllSectorNames();
            keywords.addAll(sectors);
            log.info("Loaded {} sectors from sector_companies", sectors.size());
        } catch (Exception e) {
            log.error("Failed to load sectors from sector_companies: {}", e.getMessage());
        }

        try {
            ClassPathResource resource = new ClassPathResource("keywords.txt");
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream()))) {
                int count = 0;
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        keywords.add(line);
                        count++;
                    }
                }
                log.info("Loaded {} keywords from keywords.txt", count);
            }
        } catch (Exception e) {
            log.warn("keywords.txt not found or unreadable — skipping: {}", e.getMessage());
        }

        log.info("Total unique keywords to fetch: {}", keywords.size());
        return new ArrayList<>(keywords);
    }
}
