package com.companynews.newsscheduler.service;

import com.companynews.newsscheduler.dto.NewsItem;
import com.companynews.newsscheduler.model.CompanyNews;
import com.companynews.newsscheduler.repository.CompanyNewsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class WorkerService {

    private static final Logger log = LoggerFactory.getLogger(WorkerService.class);

    private final CompanyNewsRepository repository;

    @Value("${news.limit:5}")
    private int newsLimit;

    public WorkerService(CompanyNewsRepository repository) {
        this.repository = repository;
    }

    /**
     * Saves a list of ALREADY DEDUPLICATED news items for a keyword.
     *
     * By the time items reach here, seqId checking is already done
     * by SeqIdWindowService in NseScheduler. This method only:
     * 1. Loads existing news from DB
     * 2. Adds new items to front (newest first)
     * 3. Trims to news.limit
     * 4. Saves back to DB
     *
     * No seqId logic here anymore — clean separation of concerns.
     */
    @Transactional
    public void processAndSave(String keyword, List<NewsItem> newItems) {
        if (newItems == null || newItems.isEmpty()) return;

        // Load existing row or create new one
        Optional<CompanyNews> existing = repository.findByKeyword(keyword);
        CompanyNews record;
        List<NewsItem> currentNews;

        if (existing.isPresent()) {
            record = existing.get();
            currentNews = record.getNews() != null
                ? new ArrayList<>(record.getNews())
                : new ArrayList<>();
        } else {
            record = new CompanyNews();
            record.setKeyword(keyword);
            record.setSentiments("");
            currentNews = new ArrayList<>();
        }

        // Add new items to the front — newest first
        for (NewsItem item : newItems) {
            currentNews.add(0, item);
        }

        // Trim to limit
        if (currentNews.size() > newsLimit) {
            currentNews = currentNews.subList(0, newsLimit);
        }

        // Save
        record.setNews(currentNews);
        record.setLastUpdated(LocalDateTime.now());
        repository.save(record);

        log.info("Saved {} new item(s) for keyword: {}", newItems.size(), keyword);
    }
}
