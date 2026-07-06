package com.companycode.nse.controller;

import com.companycode.nse.dto.SectorTabDto;
import com.companycode.nse.entity.SectorCompanies;
import com.companycode.nse.repository.SectorCompaniesRepository;
import com.companycode.nse.service.NseSectorSyncService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Exposes sector data from the {@code company_sectors} table.
 *
 * <p>All endpoints are prefixed with {@code /api/nse-code}.
 * The ProxyController in main-api-service routes {@code /api/nse-code/**} to this
 * service on port 8082.
 */
@RestController
@RequestMapping("/api/nse-code")
public class SectorController {

    private static final Logger logger = LogManager.getLogger(SectorController.class);

    private final SectorCompaniesRepository repository;
    private final NseSectorSyncService      sectorSyncService;

    public SectorController(SectorCompaniesRepository repository,
                            NseSectorSyncService sectorSyncService) {
        this.repository        = repository;
        this.sectorSyncService = sectorSyncService;
    }

    /**
     * Returns the list of Domestic Market sector tabs in display-name order.
     * Only sectors with a configured news keyword are included.
     *
     * <p>Response shape: {@code [{ "displayName": "IT", "newsKeyword": "IT" }, ...]}
     */
    @GetMapping("/sectors")
    public ResponseEntity<List<SectorTabDto>> getDomesticSectors() {
        List<Object[]> rows = repository.findDistinctDomesticSectors();
        List<SectorTabDto> tabs = rows.stream()
                .map(r -> new SectorTabDto((String) r[0], (String) r[1]))
                .sorted(Comparator.comparing(SectorTabDto::getDisplayName))
                .collect(Collectors.toList());
        logger.debug("GET /sectors — returning {} tabs", tabs.size());
        return ResponseEntity.ok(tabs);
    }

    /**
     * Returns all companies in a given sector display name.
     *
     * <p>Response shape: {@code [{ "symbol": "INFY", "companyName": "...", "isin": "..." }, ...]}
     */
    @GetMapping("/sectors/{displayName}/companies")
    public ResponseEntity<List<Map<String, String>>> getCompaniesBySector(
            @PathVariable String displayName) {
        List<SectorCompanies> companies = repository.findByDisplayName(displayName);
        List<Map<String, String>> result = companies.stream().map(c -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("symbol",      c.getSymbol());
            m.put("companyName", c.getCompanyName());
            m.put("isin",        c.getIsin() != null ? c.getIsin() : "");
            return m;
        }).sorted(Comparator.comparing(m -> m.get("companyName"))).collect(Collectors.toList());
        logger.debug("GET /sectors/{}/companies — {} companies", displayName, result.size());
        return ResponseEntity.ok(result);
    }

    /**
     * Returns all sectors for a specific NSE symbol (a company can belong to multiple).
     * Used by the company detail page to show sector badges.
     *
     * <p>Returns an empty list if the symbol is not in the Nifty 500.
     */
    @GetMapping("/sectors/company/{symbol}")
    public ResponseEntity<List<Map<String, String>>> getSectorForSymbol(@PathVariable String symbol) {
        List<SectorCompanies> rows = repository.findBySymbol(symbol.toUpperCase());
        List<Map<String, String>> result = rows.stream().map(c -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("symbol",      c.getSymbol());
            m.put("displayName", c.getDisplayName());
            m.put("industry",    c.getIndustry());
            m.put("newsKeyword", c.getNewsKeyword() != null ? c.getNewsKeyword() : "");
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    /**
     * Manually triggers a full sector sync from the bundled CSV.
     * Use this after updating {@code data/ind_nifty500list.csv} without redeploying.
     */
    @PostMapping("/admin/refresh-sectors")
    public ResponseEntity<Map<String, Object>> refreshSectors() {
        logger.info("Manual sector refresh triggered via admin endpoint");
        sectorSyncService.syncSectors();
        long count = repository.count();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status",        "ok");
        resp.put("totalCompanies", count);
        return ResponseEntity.ok(resp);
    }
}
