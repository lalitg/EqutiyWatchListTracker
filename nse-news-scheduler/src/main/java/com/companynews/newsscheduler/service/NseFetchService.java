package com.companynews.newsscheduler.service;
 
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.companynews.newsscheduler.config.NewsLimitConfig;
import com.companynews.newsscheduler.dto.AnnouncementItem;
import com.companynews.newsscheduler.dto.EventCalendarItem;
import com.companynews.newsscheduler.dto.NewsItem;
import com.companynews.newsscheduler.model.CompanyNews;
import com.companynews.newsscheduler.model.CompanyUpcomingEvents;
import com.companynews.newsscheduler.repository.CompanyNewsRepository;
import com.companynews.newsscheduler.repository.CompanyUpcomingEventsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
 
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
 
@Service
@RequiredArgsConstructor
@Slf4j
public class NseFetchService {
 
    private final CompanyUpcomingEventsRepository eventsRepo;
    private final CompanyNewsRepository newsRepo;
    private final NewsLimitConfig limitConfig;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
 
    @Value("${nse.api.event-calendar}")
    private String eventCalendarUrl;
 
    @Value("${nse.api.announcements}")
    private String announcementsUrl;
 
    // ── Entry point called by the scheduler ───────────────────
    public void fetchAll() {
        log.info("Starting NSE fetch...");
        fetchEventCalendar();
        fetchAnnouncements();
        log.info("NSE fetch complete.");
    }
 
    // ── API 1: Event Calendar → company_upcoming_events table ──
    private void fetchEventCalendar() {
        try {
            String json = callNseApi(eventCalendarUrl);
            if (json == null) return;
 
            EventCalendarItem[] items =
                objectMapper.readValue(json, EventCalendarItem[].class);
 
            for (EventCalendarItem item : items) {
                if (isEmpty(item.getSymbol()) || isEmpty(item.getBmDesc())) continue;
 
                NewsItem newEvent = new NewsItem(
                    item.getDate(),
                    item.getBmDesc(),
                    null   // events have no link
                );
 
                // limit for events: always use default (5 or 15)
                int limit = limitConfig.getLimitFor(item.getSymbol());
                upsertEvents(item.getSymbol(), newEvent, limit);
            }
        } catch (Exception e) {
            log.error("Error fetching event calendar: {}", e.getMessage());
        }
    }
 
    // ── API 2: Announcements → company_news table ──────────────
    private void fetchAnnouncements() {
        try {
            String json = callNseApi(announcementsUrl);
            if (json == null) return;
 
            // The announcements API wraps its array in a 'data' key.
            // We read the raw JSON tree and extract the 'data' node.
            var root = objectMapper.readTree(json);
            var dataNode = root.path("data");
            AnnouncementItem[] items;
            if (dataNode.isMissingNode()) {
                items = objectMapper.readValue(json, AnnouncementItem[].class);
            } else {
                items = objectMapper.treeToValue(dataNode, AnnouncementItem[].class);
            }
 
            for (AnnouncementItem item : items) {
                if (isEmpty(item.getSymbol()) || isEmpty(item.getAttchmntText())) continue;
 
                NewsItem newsItem = new NewsItem(
                    item.getAnDt(),
                    item.getAttchmntText(),
                    item.getAttchmntFile()
                );
 
                int limit = limitConfig.getLimitFor(item.getSymbol());
                upsertNews(item.getSymbol(), newsItem, limit);
            }
        } catch (Exception e) {
            log.error("Error fetching announcements: {}", e.getMessage());
        }
    }
 
    // ── UPSERT for events ─────────────────────────────────────
    // 'Upsert' = Insert if not exists, Update if exists.
    // We load the existing JSON array, add the new item at index 0
    // (newest first), trim to limit, then save back.
    public void upsertEvents(String symbol, NewsItem newItem, int limit) {
        try {
            CompanyUpcomingEvents row = eventsRepo
                .findByCompanySymbol(symbol)
                .orElse(new CompanyUpcomingEvents());
 
            row.setCompanySymbol(symbol);
 
            // Load existing items from JSON string
            List<NewsItem> items = parseJsonList(row.getNews());
 
            // Avoid exact duplicates (same date + same summary)
            boolean alreadyExists = items.stream().anyMatch(i ->
                eq(i.getDate(), newItem.getDate()) &&
                eq(i.getSummary(), newItem.getSummary()));
            if (alreadyExists) return;
 
            // Add newest at front
            items.add(0, newItem);
 
            // Trim to limit (extra items are just dropped — events
            // are archived separately by the ArchiveService)
            if (items.size() > limit) {
                items = items.subList(0, limit);
            }
 
            row.setNews(objectMapper.writeValueAsString(items));
            eventsRepo.save(row);
 
        } catch (Exception e) {
            log.error("Error upserting events for {}: {}", symbol, e.getMessage());
        }
    }
 
    // ── UPSERT for news ───────────────────────────────────────
    public void upsertNews(String symbol, NewsItem newItem, int limit) {
        try {
            CompanyNews row = newsRepo
                .findByCompanySymbol(symbol)
                .orElse(new CompanyNews());
 
            row.setCompanySymbol(symbol);
            List<NewsItem> items = parseJsonList(row.getNews());
 
            boolean alreadyExists = items.stream().anyMatch(i ->
                eq(i.getSummary(), newItem.getSummary()));
            if (alreadyExists) return;
 
            items.add(0, newItem);
 
            if (items.size() > limit) {
                items = items.subList(0, limit);
            }
 
            row.setNews(objectMapper.writeValueAsString(items));
            newsRepo.save(row);
 
        } catch (Exception e) {
            log.error("Error upserting news for {}: {}", symbol, e.getMessage());
        }
    }
 
    // ── Helpers ───────────────────────────────────────────────
 
    // Parses a JSON string like '[{...},{...}]' into a List<NewsItem>.
    // Returns empty list if the string is null or blank.
    private List<NewsItem> parseJsonList(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json,
                new TypeReference<List<NewsItem>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
 
    // Calls the NSE API with browser-spoofing headers.
    // NSE blocks plain HTTP clients; these headers make it look like Chrome.
    String callNseApi(String url) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36");
            headers.set("Accept", "application/json, text/plain, */*");
            headers.set("Referer", "https://www.nseindia.com/");
            headers.set("Origin", "https://www.nseindia.com");
 
            ResponseEntity<String> resp = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
 
            return resp.getStatusCode().is2xxSuccessful() ? resp.getBody() : null;
        } catch (Exception e) {
            log.error("NSE API call failed for {}: {}", url, e.getMessage());
            return null;
        }
    }
 
    private boolean isEmpty(String s) { return s == null || s.isBlank(); }
    private boolean eq(String a, String b) {
        return a != null && a.equals(b);
    }
}
