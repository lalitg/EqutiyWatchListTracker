package com.companynews.newsscheduler.service;
 
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.companynews.newsscheduler.dto.NewsItem;
import com.companynews.newsscheduler.model.*;
import com.companynews.newsscheduler.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
 
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
 
@Service
@RequiredArgsConstructor
@Slf4j
public class ArchiveService {
 
    private final CompanyUpcomingEventsRepository eventsRepo;
    private final CompanyEventsArchiveRepository eventsArchiveRepo;
    private final CompanyNewsArchiveRepository newsArchiveRepo;
    private final ObjectMapper objectMapper;
 
    // ── Archive expired events (called by scheduler at midnight) ─
    public void archiveExpiredEvents() {
        log.info("Running event archival job...");
        LocalDate today = LocalDate.now();
        int totalArchived = 0;
 
        // Load every row from company_upcoming_events
        List<CompanyUpcomingEvents> allRows = eventsRepo.findAll();
 
        for (CompanyUpcomingEvents row : allRows) {
            try {
                List<NewsItem> items = parseJsonList(row.getNews());
                List<NewsItem> active  = new ArrayList<>();
                List<NewsItem> expired = new ArrayList<>();
 
                // Separate items into active (future) and expired (past)
                for (NewsItem item : items) {
                    if (isExpired(item.getDate(), today)) {
                        expired.add(item);
                    } else {
                        active.add(item);
                    }
                }
 
                if (expired.isEmpty()) continue;
 
                // Archive each expired item as a separate row
                for (NewsItem expiredItem : expired) {
                    CompanyEventsArchive archive = new CompanyEventsArchive();
                    archive.setCompanySymbol(row.getCompanySymbol());
                    archive.setEventData(
                        objectMapper.writeValueAsString(expiredItem));
                    eventsArchiveRepo.save(archive);
                }
 
                // Update the live row with only the active (future) items
                row.setNews(objectMapper.writeValueAsString(active));
                eventsRepo.save(row);
                totalArchived += expired.size();
 
            } catch (Exception e) {
                log.error("Error archiving events for {}: {}",
                    row.getCompanySymbol(), e.getMessage());
            }
        }
        log.info("Event archival complete. Archived {} events.", totalArchived);
    }
 
    // ── Archive a single news item that was displaced by the limit ─
    // Called from NseFetchService when the list overflows.
    public void archiveNewsItem(String symbol, NewsItem item) {
        try {
            CompanyNewsArchive archive = new CompanyNewsArchive();
            archive.setCompanySymbol(symbol);
            archive.setNewsData(objectMapper.writeValueAsString(item));
            newsArchiveRepo.save(archive);
        } catch (Exception e) {
            log.error("Failed to archive news item for {}: {}", symbol, e.getMessage());
        }
    }
 
    // ── Helpers ───────────────────────────────────────────────
 
    // Returns true if the event's date is before today
    // (i.e., the event has already happened).
    private boolean isExpired(String dateStr, LocalDate today) {
        if (dateStr == null || dateStr.isBlank()) return false;
        try {
            // Try common date formats from NSE
            for (String pattern : new String[]{
                    "yyyy-MM-dd", "dd-MM-yyyy", "dd-MMM-yyyy"}) {
                try {
                    LocalDate eventDate = LocalDate.parse(
                        dateStr, DateTimeFormatter.ofPattern(pattern));
                    // 'before today' means the event day has fully passed
                    return eventDate.isBefore(today);
                } catch (DateTimeParseException ignored) {}
            }
        } catch (Exception e) {
            log.warn("Could not parse event date '{}'", dateStr);
        }
        return false;
    }
 
    private List<NewsItem> parseJsonList(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json,
                new TypeReference<List<NewsItem>>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
