package com.companycode.nse.service;

import com.companycode.nse.entity.IndexCompany;
import com.companycode.nse.repository.IndexCompanyRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Populates the {@code company_indices} table from 13 bundled Nifty index CSVs.
 *
 * <p>CSVs are sourced from niftyindices.com and committed under
 * {@code data/indices/}. Update them manually each quarter when NSE rebalances.
 *
 * <p>Each sync is a full refresh — all rows deleted then reinserted.
 */
@Service
public class NseIndexSyncService {

    private static final Logger logger = LogManager.getLogger(NseIndexSyncService.class);

    /** Ordered list of {indexKey, displayName, csvFileName} for all 13 indices. */
    private static final String[][] INDEX_CONFIG = {
        {"nifty50",            "Nifty 50",            "ind_nifty50list.csv"},
        {"niftynext50",        "Nifty Next 50",        "ind_niftynext50list.csv"},
        {"nifty100",           "Nifty 100",            "ind_nifty100list.csv"},
        {"nifty200",           "Nifty 200",            "ind_nifty200list.csv"},
        {"nifty500",           "Nifty 500",            "ind_nifty500list.csv"},
        {"niftymidcap50",      "Nifty Midcap 50",      "ind_niftymidcap50list.csv"},
        {"niftymidcap100",     "Nifty Midcap 100",     "ind_niftymidcap100list.csv"},
        {"niftymidcap150",     "Nifty Midcap 150",     "ind_niftymidcap150list.csv"},
        {"niftysmallcap50",    "Nifty Smallcap 50",    "ind_niftysmallcap50list.csv"},
        {"niftysmallcap100",   "Nifty Smallcap 100",   "ind_niftysmallcap100list.csv"},
        {"niftysmallcap250",   "Nifty Smallcap 250",   "ind_niftysmallcap250list.csv"},
        {"niftylargemidcap250","Nifty LargeMidcap 250","ind_niftylargemidcap250list.csv"},
        {"niftymicrocap250",   "Nifty Microcap 250",   "ind_niftymicrocap250_list.csv"},
    };

    private final IndexCompanyRepository repository;

    public NseIndexSyncService(IndexCompanyRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void syncIndices() {
        logger.info("Starting index sync from {} bundled CSVs", INDEX_CONFIG.length);
        try {
            List<IndexCompany> batch = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();

            for (String[] cfg : INDEX_CONFIG) {
                String indexKey   = cfg[0];
                String displayName = cfg[1];
                String csvFile    = "data/indices/" + cfg[2];

                InputStream is = getClass().getClassLoader().getResourceAsStream(csvFile);
                if (is == null) {
                    logger.warn("CSV not found on classpath: {} — skipping", csvFile);
                    continue;
                }

                int count = 0;
                int symbolIdx = -1, companyIdx = -1, isinIdx = -1, industryIdx = -1;
                boolean header = true;

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] cols = parseCsvLine(line);

                        if (header) {
                            for (int i = 0; i < cols.length; i++) {
                                switch (cols[i].trim()) {
                                    case "Symbol"       -> symbolIdx   = i;
                                    case "Company Name" -> companyIdx  = i;
                                    case "ISIN Code"    -> isinIdx     = i;
                                    case "Industry"     -> industryIdx = i;
                                }
                            }
                            if (symbolIdx < 0 || companyIdx < 0) {
                                logger.error("CSV {} missing required columns", csvFile);
                                break;
                            }
                            header = false;
                            continue;
                        }

                        int maxRequired = Math.max(symbolIdx, companyIdx);
                        if (cols.length <= maxRequired) continue;

                        String symbol      = cols[symbolIdx].trim();
                        String companyName = cols[companyIdx].trim();
                        String isin        = (isinIdx >= 0 && cols.length > isinIdx) ? cols[isinIdx].trim() : null;
                        String industry    = (industryIdx >= 0 && cols.length > industryIdx) ? cols[industryIdx].trim() : null;

                        if (symbol.isEmpty() || companyName.isEmpty()) continue;

                        IndexCompany ic = new IndexCompany();
                        ic.setSymbol(symbol);
                        ic.setCompanyName(companyName);
                        ic.setIsin(isin);
                        ic.setIndustry(industry);
                        ic.setIndexKey(indexKey);
                        ic.setDisplayName(displayName);
                        ic.setLastUpdated(now);
                        batch.add(ic);
                        count++;
                    }
                }
                logger.info("Loaded {} companies from {}", count, csvFile);
            }

            repository.deleteAllBulk();
            repository.saveAll(batch);
            logger.info("Index sync complete — {} total rows across {} indices", batch.size(), INDEX_CONFIG.length);

        } catch (Exception e) {
            logger.error("Index sync failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to sync index companies", e);
        }
    }

    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }
}
