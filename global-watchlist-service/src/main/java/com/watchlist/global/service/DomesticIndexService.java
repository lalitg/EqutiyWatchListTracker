package com.watchlist.global.service;

import com.watchlist.global.client.NseClient;
import com.watchlist.global.model.CompanyMembershipsResponse.IndexLabel;
import com.watchlist.global.model.IndexCompanyEntry;
import com.watchlist.global.model.IndexSummary;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DomesticIndexService {

    private static final Logger logger = LogManager.getLogger(DomesticIndexService.class);

    // Ordered list of domestic broad-market indices
    private static final List<String[]> DOMESTIC_INDICES = List.of(
        new String[]{"NIFTY 50",             "Nifty 50",           "true"},
        new String[]{"NIFTY 100",            "Nifty 100",          "true"},
        new String[]{"NIFTY 200",            "Nifty 200",          "true"},
        new String[]{"NIFTY 500",            "Nifty 500",          "true"},
        new String[]{"NIFTY MIDCAP 100",     "Nifty Midcap 100",   "true"},
        new String[]{"NIFTY LARGEMIDCAP 250","Nifty LargeMidcap",  "true"},
        new String[]{"NIFTY SMLCAP 100",     "Nifty Smallcap 100", "true"}
    );

    // Ordered list of sectoral indices
    private static final List<String[]> SECTOR_INDICES = List.of(
        new String[]{"NIFTY BANK",                       "Banking",              "true"},
        new String[]{"NIFTY PRIVATE BANK",               "Pvt Bank",             "true"},
        new String[]{"NIFTY PSU BANK",                   "PSU Bank",             "true"},
        new String[]{"NIFTY IT",                         "IT",                   "true"},
        new String[]{"NIFTY AUTO",                       "Auto",                 "true"},
        new String[]{"NIFTY PHARMA",                     "Pharma",               "true"},
        new String[]{"NIFTY HEALTHCARE INDEX",           "Healthcare",           "true"},
        new String[]{"NIFTY FMCG",                       "FMCG",                 "true"},
        new String[]{"NIFTY FINANCIAL SERVICES",         "Financial Services",   "true"},
        new String[]{"NIFTY FINANCIAL SERVICES 25 50",   "Fin Services 25/50",   "true"},
        new String[]{"NIFTY FINANCIAL SERVICES EX-BANK", "Fin Services Ex-Bank", "true"},
        new String[]{"NIFTY METAL",                      "Metal",                "true"},
        new String[]{"NIFTY REALTY",                     "Realty",               "true"},
        new String[]{"NIFTY ENERGY",                     "Energy",               "true"},
        new String[]{"NIFTY OIL AND GAS",                "Oil & Gas",            "true"},
        new String[]{"NIFTY CONSUMER DURABLES",          "Consumer Durables",    "true"},
        new String[]{"NIFTY CHEMICALS",                  "Chemicals",            "true"},
        new String[]{"NIFTY MEDIA",                      "Media",                "true"},
        new String[]{"NIFTY MIDSMALL HEALTHCARE",        "MidSmall Health",      "true"},
        new String[]{"NIFTY MIDSMALL IT & TELECOM",      "MidSmall IT",          "true"},
        new String[]{"NIFTY MIDSMALL FINANCIAL SERVICES","MidSmall FinServ",     "true"}
    );

    private final NseClient nseClient;

    // Cached headers — refreshed every 5 min by the scheduler
    private final Map<String, IndexSummary> domesticCache = new ConcurrentHashMap<>();
    private final Map<String, IndexSummary> sectorCache   = new ConcurrentHashMap<>();

    // Separate reverse maps rebuilt independently so concurrent refreshes don't duplicate labels
    private volatile Map<String, List<IndexLabel>> domesticMemberships = new ConcurrentHashMap<>();
    private volatile Map<String, List<IndexLabel>> sectorMemberships   = new ConcurrentHashMap<>();

    public DomesticIndexService(NseClient nseClient) {
        this.nseClient = nseClient;
    }

    // -------------------------------------------------------------------------
    // Refresh (called by scheduler)
    // -------------------------------------------------------------------------

    public void refreshDomesticIndices() {
        logger.info("Refreshing domestic index headers...");
        Map<String, List<IndexLabel>> memberships = new HashMap<>();
        for (String[] meta : DOMESTIC_INDICES) {
            IndexSummary summary = nseClient.fetchIndexHeader(meta[0]);
            if (summary != null) {
                summary.setDisplayName(meta[1]);
                summary.setHasConstituents(Boolean.parseBoolean(meta[2]));
                domesticCache.put(meta[0], summary);
            }
            IndexLabel label = new IndexLabel(meta[0], meta[1], "DOMESTIC");
            for (IndexCompanyEntry company : nseClient.fetchIndexCompanies(meta[0])) {
                memberships.computeIfAbsent(company.getSymbol(), k -> new ArrayList<>()).add(label);
            }
        }
        domesticMemberships = new ConcurrentHashMap<>(memberships);
        logger.info("Domestic index headers refreshed — {} entries, {} symbols mapped", domesticCache.size(), memberships.size());
    }

    public void refreshSectorIndices() {
        logger.info("Refreshing sector index headers...");
        Map<String, List<IndexLabel>> memberships = new HashMap<>();
        for (String[] meta : SECTOR_INDICES) {
            IndexSummary summary = nseClient.fetchIndexHeader(meta[0]);
            if (summary != null) {
                summary.setDisplayName(meta[1]);
                summary.setHasConstituents(Boolean.parseBoolean(meta[2]));
                sectorCache.put(meta[0], summary);
            }
            IndexLabel label = new IndexLabel(meta[0], meta[1], "SECTOR");
            for (IndexCompanyEntry company : nseClient.fetchIndexCompanies(meta[0])) {
                memberships.computeIfAbsent(company.getSymbol(), k -> new ArrayList<>()).add(label);
            }
        }
        sectorMemberships = new ConcurrentHashMap<>(memberships);
        logger.info("Sector index headers refreshed — {} entries, {} symbols mapped", sectorCache.size(), memberships.size());
    }

    // -------------------------------------------------------------------------
    // Read (called by controller)
    // -------------------------------------------------------------------------

    public List<IndexSummary> getAllDomesticIndices() {
        List<IndexSummary> result = new ArrayList<>();
        for (String[] meta : DOMESTIC_INDICES) {
            IndexSummary s = domesticCache.get(meta[0]);
            if (s != null) result.add(s);
        }
        return result;
    }

    public List<IndexSummary> getAllSectorIndices() {
        List<IndexSummary> result = new ArrayList<>();
        for (String[] meta : SECTOR_INDICES) {
            IndexSummary s = sectorCache.get(meta[0]);
            if (s != null) result.add(s);
        }
        return result;
    }

    /** On-demand — fetches constituent companies for a domestic/sector index. */
    public List<IndexCompanyEntry> getIndexCompanies(String nseParam) {
        logger.info("Fetching companies for index '{}'", nseParam);
        return nseClient.fetchIndexCompanies(nseParam);
    }

    /** Returns the canonical NSE param for a URL-friendly index key (e.g. "NIFTY-50" → "NIFTY 50"). */
    public String resolveNseParam(String urlKey) {
        // Try exact match first — preserves meaningful hyphens like "EX-BANK"
        for (String[] meta : DOMESTIC_INDICES) {
            if (meta[0].equalsIgnoreCase(urlKey)) return meta[0];
        }
        for (String[] meta : SECTOR_INDICES) {
            if (meta[0].equalsIgnoreCase(urlKey)) return meta[0];
        }
        // Fallback: replace hyphens with spaces for URL-friendly forms like "NIFTY-50"
        String normalised = urlKey.replace("-", " ").toUpperCase();
        for (String[] meta : DOMESTIC_INDICES) {
            if (meta[0].equalsIgnoreCase(normalised)) return meta[0];
        }
        for (String[] meta : SECTOR_INDICES) {
            if (meta[0].equalsIgnoreCase(normalised)) return meta[0];
        }
        return urlKey;
    }

    /**
     * Returns the short display name for a given NSE index key
     * (e.g. "NIFTY PSU BANK" → "PSU Bank"), or null if not in our lists.
     */
    public String getDisplayName(String nseKey) {
        for (String[] meta : DOMESTIC_INDICES) {
            if (meta[0].equalsIgnoreCase(nseKey)) return meta[1];
        }
        for (String[] meta : SECTOR_INDICES) {
            if (meta[0].equalsIgnoreCase(nseKey)) return meta[1];
        }
        return null;
    }

    /** Returns "SECTOR" or "DOMESTIC" for a given NSE key, null if not in our lists. */
    public String resolveType(String nseKey) {
        for (String[] meta : DOMESTIC_INDICES) {
            if (meta[0].equalsIgnoreCase(nseKey)) return "DOMESTIC";
        }
        for (String[] meta : SECTOR_INDICES) {
            if (meta[0].equalsIgnoreCase(nseKey)) return "SECTOR";
        }
        return null;
    }

    /**
     * Returns the precomputed list of index/sector memberships for a given symbol,
     * or an empty list if the symbol was not found in any tracked index during the
     * last refresh. Avoids the broken per-symbol NSE quote-equity API.
     */
    public List<IndexLabel> getSymbolMemberships(String symbol) {
        String upper = symbol.toUpperCase();
        List<IndexLabel> result = new ArrayList<>();
        result.addAll(domesticMemberships.getOrDefault(upper, Collections.emptyList()));
        result.addAll(sectorMemberships.getOrDefault(upper, Collections.emptyList()));
        return result;
    }
}
