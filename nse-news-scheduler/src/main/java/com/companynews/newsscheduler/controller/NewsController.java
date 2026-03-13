package com.companynews.newsscheduler.controller;

import com.companynews.newsscheduler.dto.NseAnnouncement;
import com.companynews.newsscheduler.dto.NewsItem;
import com.companynews.newsscheduler.model.CompanyNews;
import com.companynews.newsscheduler.repository.CompanyNewsRepository;
import com.companynews.newsscheduler.service.NseFetchService;
import com.companynews.newsscheduler.service.SeqIdWindowService;
import com.companynews.newsscheduler.service.WorkerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/news")
public class NewsController {

    private static final Logger log = LoggerFactory.getLogger(NewsController.class);

    private final CompanyNewsRepository repository;
    private final NseFetchService nseFetchService;
    private final WorkerService workerService;
    private final SeqIdWindowService seqIdWindowService;

    public NewsController(CompanyNewsRepository repository,
                          NseFetchService nseFetchService,
                          WorkerService workerService,
                          SeqIdWindowService seqIdWindowService) {
        this.repository = repository;
        this.nseFetchService = nseFetchService;
        this.workerService = workerService;
        this.seqIdWindowService = seqIdWindowService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getNews(@RequestParam String key) {
        log.info("GET /api/news?key={}", key);

        // Check DB first
        Optional<CompanyNews> existing = repository.findByKeyword(key);
        if (existing.isPresent()) {
            log.info("Found existing news for: {}", key);
            return ResponseEntity.ok(buildResponse(existing.get()));
        }

        // Not in DB — fetch on demand from NSE
        log.info("Not found in DB — triggering on-demand fetch for: {}", key);
        List<NseAnnouncement> fetched = nseFetchService.fetchAllAnnouncements();

        // Filter to matching symbol, deduplicate via window, extract NewsItems
        List<NewsItem> matching = fetched.stream()
            .filter(a -> key.equalsIgnoreCase(a.getNewsItem().getSymbol()))
            .filter(a -> {
                if (seqIdWindowService.isAlreadySeen(a.getSeqId())) {
                    return false;  // duplicate
                }
                seqIdWindowService.markAsSeen(a.getSeqId());
                return true;  // new
            })
            .map(NseAnnouncement::getNewsItem)
            .toList();

        if (!matching.isEmpty()) {
            workerService.processAndSave(key, matching);
        }

        // Read back what was just saved
        Optional<CompanyNews> saved = repository.findByKeyword(key);
        if (saved.isPresent()) {
            return ResponseEntity.ok(buildResponse(saved.get()));
        }

        log.warn("No news found for: {} even after on-demand fetch", key);
        return ResponseEntity.ok(buildEmptyResponse(key));
    }

    private Map<String, Object> buildResponse(CompanyNews companyNews) {
        Map<String, Object> response = new HashMap<>();
        response.put("keyword", companyNews.getKeyword());
        response.put("sentiments",
            companyNews.getSentiments() != null ? companyNews.getSentiments() : "");
        response.put("news", companyNews.getNews());
        response.put("lastUpdated", companyNews.getLastUpdated());
        return response;
    }

    private Map<String, Object> buildEmptyResponse(String key) {
        Map<String, Object> response = new HashMap<>();
        response.put("keyword", key);
        response.put("sentiments", "");
        response.put("news", List.of());
        response.put("lastUpdated", null);
        return response;
    }
}
