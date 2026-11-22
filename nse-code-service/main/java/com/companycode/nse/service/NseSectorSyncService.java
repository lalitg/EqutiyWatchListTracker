package com.companycode.nse.service;

import com.companycode.nse.entity.SectorCompanies;
import com.companycode.nse.repository.SectorCompaniesRepository;
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
 * Populates the {@code company_sectors} table from the bundled Nifty 500 CSV.
 *
 * <p>The CSV ({@code data/ind_nifty500list.csv}) is sourced from NSE Indices Ltd.
 * (niftyindices.com) and committed to the repository. Update it manually each
 * quarter (January, April, July, October) when NSE rebalances the index.
 *
 * <p>Column headers are located by name rather than position so minor structural
 * changes to the CSV do not silently corrupt data.
 *
 * <p>Each sync is a full refresh — all existing rows are deleted and reinserted.
 */
@Service
public class NseSectorSyncService {

    private static final Logger logger = LogManager.getLogger(NseSectorSyncService.class);

    private static final String CSV_CLASSPATH = "data/ind_nifty500list.csv";

    /**
     * Maps raw CSV {@code Industry} values to {displayName, newsKeyword}.
     *
     * Power and Oil Gas both map to Energy (same display sector, same news keyword).
     * Industries absent from this map get the CSV value as displayName and null
     * newsKeyword — they appear on company pages but not as Domestic tab sectors.
     */
    private static final Map<String, String[]> INDUSTRY_MAP = new LinkedHashMap<>();
    static {
        INDUSTRY_MAP.put("Information Technology",            new String[]{"IT",                 "IT"});
        INDUSTRY_MAP.put("Healthcare",                        new String[]{"Pharma",             "Pharma"});
        INDUSTRY_MAP.put("Financial Services",                new String[]{"Financial Services", "Financial Services"});
        INDUSTRY_MAP.put("Automobile and Auto Components",    new String[]{"Auto",               "Auto"});
        INDUSTRY_MAP.put("Fast Moving Consumer Goods",        new String[]{"FMCG",               "FMCG"});
        INDUSTRY_MAP.put("Power",                             new String[]{"Energy",             "Energy"});
        INDUSTRY_MAP.put("Oil Gas & Consumable Fuels",        new String[]{"Energy",             "Energy"});
        INDUSTRY_MAP.put("Metals & Mining",                   new String[]{"Metals",             "Metals"});
        INDUSTRY_MAP.put("Construction",                      new String[]{"Infra",              "Infrastructure"});
        INDUSTRY_MAP.put("Construction Materials",            new String[]{"Infra",              "Infrastructure"});
        INDUSTRY_MAP.put("Media Entertainment & Publication", new String[]{"Media",              "Media"});
        INDUSTRY_MAP.put("Chemicals",                         new String[]{"Chemicals",          "Chemicals"});
        INDUSTRY_MAP.put("Capital Goods",                     new String[]{"Capital Goods",      "Capital Goods"});
        INDUSTRY_MAP.put("Consumer Durables",                 new String[]{"Consumer Durables",  "Consumer Durables"});
        INDUSTRY_MAP.put("Consumer Services",                 new String[]{"Consumer Services",  "Consumer Services"});
        INDUSTRY_MAP.put("Realty",                            new String[]{"Realty",             "Realty"});
        INDUSTRY_MAP.put("Telecommunication",                 new String[]{"Telecom",            "Telecom"});
        INDUSTRY_MAP.put("Services",                          new String[]{"Services",           "Services"});
    }

    private final SectorCompaniesRepository repository;

    public NseSectorSyncService(SectorCompaniesRepository repository) {
        this.repository = repository;
    }

    /**
     * Full refresh of {@code company_sectors} from the Nifty 500 classpath CSV.
     */
    @Transactional
    public void syncSectors() {
        logger.info("Starting sector sync from classpath CSV: {}", CSV_CLASSPATH);
        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream(CSV_CLASSPATH);
            if (is == null) {
                logger.error("CSV not found on classpath: {} — skipping sector sync", CSV_CLASSPATH);
                return;
            }

            List<SectorCompanies> batch = new ArrayList<>();
            int symbolIdx = -1, industryIdx = -1, companyIdx = -1, isinIdx = -1;
            boolean header = true;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] cols = parseCsvLine(line);

                    if (header) {
                        for (int i = 0; i < cols.length; i++) {
                            switch (cols[i].trim()) {
                                case "Symbol"       -> symbolIdx   = i;
                                case "Industry"     -> industryIdx = i;
                                case "Company Name" -> companyIdx  = i;
                                case "ISIN Code"    -> isinIdx     = i;
                            }
                        }
                        if (symbolIdx < 0 || industryIdx < 0 || companyIdx < 0) {
                            logger.error("CSV missing required columns. Found headers: {}", Arrays.toString(cols));
                            return;
                        }
                        header = false;
                        continue;
                    }

                    int maxRequired = Math.max(symbolIdx, Math.max(industryIdx, companyIdx));
                    if (cols.length <= maxRequired) continue;

                    String symbol      = cols[symbolIdx].trim();
                    String industry    = cols[industryIdx].trim();
                    String companyName = cols[companyIdx].trim();
                    String isin        = (isinIdx >= 0 && cols.length > isinIdx) ? cols[isinIdx].trim() : null;

                    if (symbol.isEmpty() || industry.isEmpty()) continue;

                    String[] mapping   = INDUSTRY_MAP.get(industry);
                    String displayName = (mapping != null) ? mapping[0] : industry;
                    String newsKeyword = (mapping != null) ? mapping[1] : null;

                    SectorCompanies sc = new SectorCompanies();
                    sc.setSymbol(symbol);
                    sc.setCompanyName(companyName);
                    sc.setIsin(isin);
                    sc.setIndustry(industry);
                    sc.setDisplayName(displayName);
                    sc.setNewsKeyword(newsKeyword);
                    sc.setLastUpdated(LocalDateTime.now());
                    batch.add(sc);
                }
            }

            repository.deleteAllBulk();
            repository.saveAll(batch);

            long domesticTabCount = batch.stream()
                    .filter(s -> s.getNewsKeyword() != null)
                    .map(SectorCompanies::getDisplayName)
                    .distinct().count();
            logger.info("Sector sync complete — {} companies, {} domestic tab sectors",
                    batch.size(), domesticTabCount);

        } catch (Exception e) {
            logger.error("Sector sync failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to sync sector companies", e);
        }
    }

    /** Parses one CSV line, respecting double-quoted fields that may contain commas. */
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
