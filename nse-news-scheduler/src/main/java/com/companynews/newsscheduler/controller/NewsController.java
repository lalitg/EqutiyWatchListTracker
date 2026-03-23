package com.companynews.newsscheduler.controller;

import com.companynews.newsscheduler.dto.NseAnnouncement;
import com.companynews.newsscheduler.dto.NewsItem;
import com.companynews.newsscheduler.model.CompanyNews;
import com.companynews.newsscheduler.repository.CompanyNewsRepository;
import com.companynews.newsscheduler.service.NseFetcher;
import com.companynews.newsscheduler.service.RssFetcher;
import com.companynews.newsscheduler.service.SeqIdWindow;
import com.companynews.newsscheduler.service.UrlWindow;
import com.companynews.newsscheduler.service.NewsWorker;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Validated
@RestController
@RequestMapping("/api/news")
public class NewsController {

    private static final Logger log = LoggerFactory.getLogger(NewsController.class);

    private final CompanyNewsRepository repository;
    private final NseFetcher nseFetcher;
    private final RssFetcher rssFetcher;
    private final NewsWorker newsWorker;
    private final SeqIdWindow seqIdWindow;
    private final UrlWindow urlWindow;

    public NewsController(CompanyNewsRepository repository,
                          NseFetcher nseFetcher,
                          RssFetcher rssFetcher,
                          NewsWorker newsWorker,
                          SeqIdWindow seqIdWindow,
                          UrlWindow urlWindow) {
        this.repository  = repository;
        this.nseFetcher  = nseFetcher;
        this.rssFetcher  = rssFetcher;
        this.newsWorker  = newsWorker;
        this.seqIdWindow = seqIdWindow;
        this.urlWindow   = urlWindow;
    }

    /**
     * GET /api/news?key=INFY
     * GET /api/news?key=Banking
     * GET /api/news?key=Nifty 50
     *
     * @NotBlank rejects null, empty, or whitespace-only keys before any service logic runs.
     * GlobalExceptionHandler converts the resulting ConstraintViolationException into a 400.
     *
     * Logic:
     * 1. DB cache hit → return immediately (fastest path)
     * 2. NSE on-demand fetch → filter + dedup by seqId → save
     * 3. Google RSS on-demand fetch → dedup by URL window → save
     * 4. Read back what was saved and return
     * 5. Nothing found → return empty response
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getNews(
            @RequestParam @NotBlank(message = "key must not be blank") String key) {

        log.info("GET /api/news?key={}", key);

        Optional<CompanyNews> existing = repository.findByKeyword(key);
        if (existing.isPresent()) {
            log.info("Cache hit — returning DB data for: {}", key);
            return ResponseEntity.ok(buildResponse(existing.get()));
        }

        log.info("Not in DB — fetching on-demand for: {}", key);

        List<NseAnnouncement> nseAnnouncements = nseFetcher.fetch();
        List<NewsItem> nseMatching = nseAnnouncements.stream()
            .filter(a -> key.equalsIgnoreCase(a.getNewsItem().getSymbol()))
            .filter(a -> {
                if (seqIdWindow.contains(a.getSeqId())) return false;
                seqIdWindow.add(a.getSeqId());
                return true;
            })
            .map(NseAnnouncement::getNewsItem)
            .toList();

        if (!nseMatching.isEmpty()) {
            newsWorker.saveNews(key, nseMatching);
        }

        List<NewsItem> googleItems = rssFetcher.fetch(key);
        List<NewsItem> googleNew = googleItems.stream()
            .filter(item -> urlWindow.addIfAbsent(item.getLink()))
            .toList();

        if (!googleNew.isEmpty()) {
            newsWorker.saveNews(key, googleNew);
        }

        Optional<CompanyNews> saved = repository.findByKeyword(key);
        if (saved.isPresent()) {
            return ResponseEntity.ok(buildResponse(saved.get()));
        }

        log.warn("No news found for: {} from any source", key);
        return ResponseEntity.ok(buildEmptyResponse(key));
    }

    private Map<String, Object> buildResponse(CompanyNews companyNews) {
        Map<String, Object> response = new HashMap<>();
        response.put("keyword",     companyNews.getKeyword());
        response.put("sentiments",  companyNews.getSentiments() != null ? companyNews.getSentiments() : "");
        response.put("news",        companyNews.getNews());
        response.put("lastUpdated", companyNews.getLastUpdated());
        return response;
    }

    private Map<String, Object> buildEmptyResponse(String key) {
        Map<String, Object> response = new HashMap<>();
        response.put("keyword",     key);
        response.put("sentiments",  "");
        response.put("news",        List.of());
        response.put("lastUpdated", null);
        return response;
    }
}
