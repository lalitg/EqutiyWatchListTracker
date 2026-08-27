package com.equity.sebi.controller;

import com.equity.sebi.model.BuybackMatchResult;
import com.equity.sebi.model.BuybackPocResponse;
import com.equity.sebi.model.RssMatchResult;
import com.equity.sebi.model.SebiRssPocResponse;
import com.equity.sebi.service.NseCompanyStore;
import com.equity.sebi.service.SebiBuybackService;
import com.equity.sebi.service.SebiRssService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/sebi")
public class SebiPocController {

    private static final Logger log = LogManager.getLogger(SebiPocController.class);

    private final SebiRssService rssService;
    private final SebiBuybackService buybackService;
    private final NseCompanyStore nseStore;

    public SebiPocController(SebiRssService rssService,
                              SebiBuybackService buybackService,
                              NseCompanyStore nseStore) {
        this.rssService     = rssService;
        this.buybackService = buybackService;
        this.nseStore       = nseStore;
    }

    /**
     * GET /api/v1/sebi/poc/rss
     * Full RSS feed matched to NSE.
     */
    @GetMapping("/poc/rss")
    public ResponseEntity<SebiRssPocResponse> rss() {
        if (!nseStore.isLoaded()) return ResponseEntity.status(503).build();
        return ResponseEntity.ok(rssService.fetchAndMatch());
    }

    /**
     * GET /api/v1/sebi/poc/buybacks
     * Full buyback listing matched to NSE.
     */
    @GetMapping("/poc/buybacks")
    public ResponseEntity<BuybackPocResponse> buybacks() {
        if (!nseStore.isLoaded()) return ResponseEntity.status(503).build();
        return ResponseEntity.ok(buybackService.fetchAndMatch());
    }

    /**
     * GET /api/v1/sebi/poc/status
     */
    @GetMapping("/poc/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "nseStoreLoaded", nseStore.isLoaded(),
                "nseCompanyCount", nseStore.size()
        ));
    }

    /**
     * GET /api/v1/sebi/poc/test-match?name=Wipro Limited
     */
    @GetMapping("/poc/test-match")
    public ResponseEntity<Map<String, Object>> testMatch(@RequestParam String name) {
        var result = nseStore.match(name);
        if (result.isEmpty()) {
            return ResponseEntity.ok(Map.of("input", name, "matched", false));
        }
        var m = result.get();
        return ResponseEntity.ok(Map.of(
                "input",          name,
                "matched",        true,
                "nseSymbol",      m.company().symbol(),
                "nseCompanyName", m.company().name(),
                "isin",           m.company().isin(),
                "matchTier",      m.tier().name()
        ));
    }

    /**
     * GET /api/v1/sebi/company/{symbol}
     *
     * Returns all SEBI regulatory activity for a specific NSE-listed company:
     * enforcement actions from the RSS feed + any buyback filings.
     */
    @GetMapping("/company/{symbol}")
    public ResponseEntity<Map<String, Object>> companyActivity(@PathVariable String symbol) {
        if (!nseStore.isLoaded()) {
            log.warn("NSE store not yet loaded — returning 503 for symbol {}", symbol);
            return ResponseEntity.status(503).build();
        }

        String upper = symbol.toUpperCase();
        log.info("SEBI company activity request for symbol={}", upper);

        List<RssMatchResult> enforcement = rssService.getMatchesForSymbol(upper);
        List<BuybackMatchResult> buybacks = buybackService.getMatchesForSymbol(upper);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("symbol", upper);
        response.put("enforcementActions", enforcement);
        response.put("buybacks", buybacks);
        response.put("totalEnforcement", enforcement.size());
        response.put("totalBuybacks", buybacks.size());

        return ResponseEntity.ok(response);
    }
}
