package com.companycode.nse.service;

import com.companycode.nse.entity.CompanyMaster;
import com.companycode.nse.repository.CompanyMasterRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service responsible for syncing the NSE company master list into the database.
 *
 * <p>Downloads the {@code EQUITY_L.csv} file published by NSE, which contains all
 * ~2200 actively listed companies, and performs an incremental insert — only companies
 * not already present in {@code company_master} are added.
 *
 * <p>The full company list is loaded into a map before processing to avoid issuing
 * one SELECT per row (reduces DB round-trips from ~2200 to 1).
 */
@Service
public class NseSyncService {

    private static final Logger logger = LogManager.getLogger(NseSyncService.class);

    private static final String EQUITY_CSV_URL = "https://archives.nseindia.com/content/equities/EQUITY_L.csv";

    private final CompanyMasterRepository repository;

    /**
     * Constructs the service with the required repository dependency.
     *
     * @param repository JPA repository for the {@code company_master} table
     */
    public NseSyncService(CompanyMasterRepository repository) {
        this.repository = repository;
    }

    /**
     * Downloads the NSE {@code EQUITY_L.csv} and inserts any companies not yet
     * present in the {@code company_master} table.
     *
     * <p>Steps:
     * <ol>
     *   <li>Load all existing symbols from DB into a map (single query)</li>
     *   <li>Stream the CSV line by line, skipping the header row</li>
     *   <li>For each symbol not already in the map, build a {@link CompanyMaster} entity</li>
     *   <li>Batch-insert all new entities via {@code saveAll}</li>
     * </ol>
     *
     * @throws RuntimeException wrapping any IO or DB error encountered during sync
     */
    @Transactional
    public void syncCompanies() {
        logger.info("Starting company master sync from NSE CSV...");
        try {
            // Load all existing symbols in one query to avoid per-row SELECT calls
            Map<String, CompanyMaster> existingBySymbol = repository.findAll()
                    .stream()
                    .collect(Collectors.toMap(CompanyMaster::getSymbol, c -> c));
            logger.debug("Loaded {} existing companies from DB", existingBySymbol.size());

            List<CompanyMaster> batch = new ArrayList<>();
            String line;
            boolean header = true;

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(URI.create(EQUITY_CSV_URL).toURL().openStream()))) {

                while ((line = reader.readLine()) != null) {
                    if (header) { header = false; continue; }

                    String[] data = line.split(",");
                    // CSV columns: SYMBOL, NAME OF COMPANY, SERIES, DATE OF LISTING,
                    //              PAID UP VALUE, MARKET LOT, ISIN NUMBER, FACE VALUE
                    if (data.length < 7) {
                        logger.warn("Skipping malformed CSV row: {}", line);
                        continue;
                    }

                    String symbol = data[0].trim();
                    if (existingBySymbol.containsKey(symbol)) {
                        continue; // already tracked — skip
                    }

                    CompanyMaster company = new CompanyMaster();
                    company.setSymbol(symbol);
                    company.setCompanyName(data[1].trim());
                    company.setIsin(data[6].trim()); // index 6 = ISIN NUMBER
                    company.setLastUpdated(LocalDateTime.now());
                    batch.add(company);
                    logger.debug("Queued new company for insert: {}", symbol);
                }
            }

            repository.saveAll(batch);
            logger.info("Company sync complete — inserted {} new companies (total in DB: {})",
                    batch.size(), existingBySymbol.size() + batch.size());

        } catch (Exception e) {
            logger.error("Company sync failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to sync NSE companies", e);
        }
    }
}
