package com.companynews.newsscheduler.service;

import com.companynews.newsscheduler.repository.GlobalWatchlistRepository;
import com.companynews.newsscheduler.repository.SectorRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Loads the merged keyword list from three sources for use by the Google RSS scheduler.
 *
 * <p>Sources (merged in priority order):
 * <ol>
 *   <li><b>global_watchlist table</b> — company symbols (e.g., {@code INFY}, {@code RELIANCE}).
 *       These are highest priority and always appear first.</li>
 *   <li><b>sector_companies table</b> — sector names (e.g., {@code Banking}, {@code Pharma}).</li>
 *   <li><b>keywords.txt classpath file</b> — custom macro/geopolitical keywords
 *       (e.g., {@code Nifty 50}, {@code RBI monetary policy}).</li>
 * </ol>
 *
 * <p>A {@link LinkedHashSet} is used internally to deduplicate across sources while preserving
 * insertion order. Each source is wrapped in its own {@code try-catch} so a DB outage or
 * missing file does not prevent the other sources from loading.
 */
@Service
public class KeywordLoader {

    private static final Logger log = LogManager.getLogger(KeywordLoader.class);

    private final GlobalWatchlistRepository watchlistRepository;
    private final SectorRepository sectorRepository;

    /**
     * Constructs a {@code KeywordLoader} with all required repositories injected by Spring.
     *
     * @param watchlistRepository repository for reading company symbols from the global watchlist
     * @param sectorRepository    repository for reading sector names from the sector table
     */
    public KeywordLoader(GlobalWatchlistRepository watchlistRepository,
                         SectorRepository sectorRepository) {
        this.watchlistRepository = watchlistRepository;
        this.sectorRepository    = sectorRepository;
    }

    /**
     * Loads and returns a deduplicated, ordered list of all keywords from all three sources.
     *
     * <p>WHY {@link LinkedHashSet}: a plain {@link java.util.HashSet} removes duplicates but
     * loses insertion order. {@code LinkedHashSet} preserves insertion order while still
     * deduplicating — company symbols always appear first as they are the highest-priority keywords.
     *
     * <p>Each source is loaded inside its own {@code try-catch}:
     * <ul>
     *   <li>DB failure for the watchlist does not block sector loading.</li>
     *   <li>DB failure for sectors does not block {@code keywords.txt} loading.</li>
     *   <li>Missing {@code keywords.txt} is logged as a warning, not an error.</li>
     * </ul>
     *
     * @return a deduplicated list of keywords ready for Google RSS fetching;
     *         never {@code null}, but may be empty if all sources fail
     */
    @Transactional(readOnly = true)
    public List<String> load() {
        Set<String> keywords = new LinkedHashSet<>();

        try {
            List<String> symbols = watchlistRepository.findAllSymbols();
            keywords.addAll(symbols);
            log.info("Loaded {} symbols from global_watchlist", symbols.size());
        } catch (Exception e) {
            log.error("Failed to load symbols from global_watchlist: {}", e.getMessage(), e);
        }

        try {
            List<String> sectors = sectorRepository.findAllSectorNames();
            keywords.addAll(sectors);
            log.info("Loaded {} sectors from sector_companies", sectors.size());
        } catch (Exception e) {
            log.error("Failed to load sectors from sector_companies: {}", e.getMessage(), e);
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
