package com.companycode.nse.controller;

import com.companycode.nse.entity.IndexCompany;
import com.companycode.nse.repository.IndexCompanyRepository;
import com.companycode.nse.service.NseIndexSyncService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/nse-code")
public class IndexController {

    private static final Logger logger = LogManager.getLogger(IndexController.class);

    private final IndexCompanyRepository repository;
    private final NseIndexSyncService    indexSyncService;

    public IndexController(IndexCompanyRepository repository, NseIndexSyncService indexSyncService) {
        this.repository       = repository;
        this.indexSyncService = indexSyncService;
    }

    /**
     * Returns all 13 indices with company count.
     * Response: [{ "indexKey": "nifty50", "displayName": "Nifty 50", "companyCount": 50 }, ...]
     */
    @GetMapping("/indices")
    public ResponseEntity<List<Map<String, Object>>> getAllIndices() {
        Map<String, Long> countMap = new LinkedHashMap<>();
        repository.countByIndexKey().forEach(row -> countMap.put((String) row[0], (Long) row[1]));

        List<Map<String, Object>> result = repository.findDistinctIndices().stream().map(row -> {
            String indexKey   = (String) row[0];
            String displayName = (String) row[1];
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("indexKey",      indexKey);
            m.put("displayName",   displayName);
            m.put("companyCount",  countMap.getOrDefault(indexKey, 0L));
            return m;
        }).collect(Collectors.toList());

        logger.debug("GET /indices — returning {} indices", result.size());
        return ResponseEntity.ok(result);
    }

    /**
     * Returns all companies in a given index, sorted A-Z by company name.
     * Response: [{ "symbol": "INFY", "companyName": "...", "isin": "...", "industry": "..." }, ...]
     */
    @GetMapping("/indices/{indexKey}/companies")
    public ResponseEntity<List<Map<String, String>>> getCompaniesByIndex(@PathVariable String indexKey) {
        List<IndexCompany> companies = repository.findByIndexKeyOrderByCompanyNameAsc(indexKey);
        List<Map<String, String>> result = companies.stream().map(c -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("symbol",      c.getSymbol());
            m.put("companyName", c.getCompanyName());
            m.put("isin",        c.getIsin() != null ? c.getIsin() : "");
            m.put("industry",    c.getIndustry() != null ? c.getIndustry() : "");
            return m;
        }).collect(Collectors.toList());
        logger.debug("GET /indices/{}/companies — {} companies", indexKey, result.size());
        return ResponseEntity.ok(result);
    }

    /**
     * Returns all indices a company belongs to (used for company detail page badges).
     * Response: [{ "indexKey": "nifty50", "displayName": "Nifty 50" }, ...]
     */
    @GetMapping("/indices/company/{symbol}")
    public ResponseEntity<List<Map<String, String>>> getIndicesForSymbol(@PathVariable String symbol) {
        List<IndexCompany> rows = repository.findBySymbol(symbol.toUpperCase());
        List<Map<String, String>> result = rows.stream().map(c -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("indexKey",    c.getIndexKey());
            m.put("displayName", c.getDisplayName());
            return m;
        }).sorted(Comparator.comparing(m -> m.get("displayName"))).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    /**
     * Manually triggers a full index sync from bundled CSVs.
     */
    @PostMapping("/admin/refresh-indices")
    public ResponseEntity<Map<String, Object>> refreshIndices() {
        logger.info("Manual index refresh triggered via admin endpoint");
        indexSyncService.syncIndices();
        long count = repository.count();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status",     "ok");
        resp.put("totalRows",  count);
        return ResponseEntity.ok(resp);
    }
}
