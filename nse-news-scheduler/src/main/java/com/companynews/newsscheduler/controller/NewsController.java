package com.companynews.newsscheduler.controller;

import com.companynews.newsscheduler.dto.NseAnnouncement;
import com.companynews.newsscheduler.dto.NewsItem;
import com.companynews.newsscheduler.model.CompanyNews;
import com.companynews.newsscheduler.repository.CompanyNewsRepository;
import com.companynews.newsscheduler.service.GoogleRssService;
import com.companynews.newsscheduler.service.NseFetchService;
import com.companynews.newsscheduler.service.SeqIdWindowService;
import com.companynews.newsscheduler.service.UrlWindowService;
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
    private final GoogleRssService googleRssService;
    private final WorkerService workerService;
    private final SeqIdWindowService seqIdWindowService;
    private final UrlWindowService urlWindowService;

    public NewsController(CompanyNewsRepository repository,
                          NseFetchService nseFetchService,
                          GoogleRssService googleRssService,
                          WorkerService workerService,
                          SeqIdWindowService seqIdWindowService,
                          UrlWindowService urlWindowService) {
        this.repository = repository;
        this.nseFetchService = nseFetchService;
        this.googleRssService = googleRssService;
        this.workerService = workerService;
        this.seqIdWindowService = seqIdWindowService;
        this.urlWindowService = urlWindowService;
    }

    /**
     * GET /api/news?key=INFY
     * GET /api/news?key=Banking
     * GET /api/news?key=Nifty 50
     *
     * Works for ANY keyword — company symbol, sector, or custom keyword.
     *
     * Logic:
     * 1. Check DB — if found, return immediately
     * 2. If not found — try NSE first (good for company symbols)
     * 3. If NSE has nothing — try Google RSS (works for anything)
     * 4. Save whatever we find and return it
     * 5. If truly nothing found — return empty response
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getNews(@RequestParam String key) {
        log.info("GET /api/news?key={}", key);

        // Step 1: Check DB first — fastest path
        Optional<CompanyNews> existing = repository.findByKeyword(key);
        if (existing.isPresent()) {
            log.info("Cache hit — returning DB data for: {}", key);
            return ResponseEntity.ok(buildResponse(existing.get()));
        }

        // Step 2: Not in DB — try NSE on-demand
        log.info("Not in DB — trying NSE on-demand for: {}", key);
        List<NseAnnouncement> nseAnnouncements = nseFetchService.fetchAllAnnouncements();

        List<NewsItem> nseMatching = nseAnnouncements.stream()
            .filter(a -> key.equalsIgnoreCase(a.getNewsItem().getSymbol()))
            .filter(a -> {
                if (seqIdWindowService.isAlreadySeen(a.getSeqId())) return false;
                seqIdWindowService.markAsSeen(a.getSeqId());
                return true;
            })
            .map(NseAnnouncement::getNewsItem)
            .toList();

        if (!nseMatching.isEmpty()) {
            workerService.processAndSave(key, nseMatching);
        }

        // Step 3: Also fetch from Google RSS for this keyword
        // WHY always fetch Google RSS too:
        // NSE only has corporate announcements.
        // Google RSS has broader news — articles, analysis, sector news.
        // Both together give the user richer news.
        log.info("Fetching Google RSS on-demand for: {}", key);
        List<NewsItem> googleItems = googleRssService.fetchNews(key);

        // NEW — use checkAndMark instead of separate check and mark
        List<NewsItem> googleNew = googleItems.stream()
            .filter(item -> urlWindowService.checkAndMark(item.getLink()))
            .toList();

        if (!googleNew.isEmpty()) {
            workerService.processAndSave(key, googleNew);
        }

        // Step 4: Read back whatever was saved
        Optional<CompanyNews> saved = repository.findByKeyword(key);
        if (saved.isPresent()) {
            return ResponseEntity.ok(buildResponse(saved.get()));
        }

        // Step 5: Nothing found anywhere — return empty
        log.warn("No news found for: {} from any source", key);
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
