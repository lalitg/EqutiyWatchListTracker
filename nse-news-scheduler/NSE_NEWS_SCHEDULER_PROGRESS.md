# NSE News Scheduler — Progress Tracker

**Service:** `nse-news-scheduler` | **Port:** `8084` | **DB:** `watchlisttracker`

---

## ✅ Done

### Infrastructure
- Flyway migration — auto-creates `company_news` table on first JAR startup, never drops it again
- `application.properties` — all config values externalized (port, DB, cron, limits, thread pool size)
- `AppConfig` — Jackson `ObjectMapper` configured with `JavaTimeModule` for `LocalDateTime` support

### NSE Pipeline
- `NseFetchService` — calls `https://www.nseindia.com/api/corporate-announcements?index=equities` with browser headers to avoid 403
- `NseScheduler` — fetches NSE announcements every 15 minutes via cron
- `NseScheduler` — fetches once automatically on application startup (no need to wait 15 minutes on first run)
- `SeqIdWindowService` — in-memory `TreeSet<Long>` sliding window of 20 `seq_id` values for NSE duplicate detection, zero DB queries

### Google RSS Pipeline
- `GoogleRssService` — fetches and parses Google RSS XML feed for any keyword (company symbol, sector, or custom keyword)
- `GoogleRssScheduler` — fetches news for all keywords once on startup and then every day at 8 AM
- `GoogleRssScheduler` — `ExecutorService` with 2 worker threads for parallel keyword processing
- `UrlWindowService` — in-memory `HashSet` + `Queue` sliding window of 100 URLs for Google RSS duplicate detection
- `KeywordLoaderService` — loads keywords from 3 sources: `global_watchlist` table, `sectors` table, `keywords.txt` file, merges into one deduplicated list

### Storage
- `WorkerService` — saves news to DB, adds newest items to front of list, trims to configured limit (default 5), handles INSERT vs UPDATE automatically
- `company_news` table — one row per keyword, news stored as JSONB array `[{date, summary, link}]`, `sentiments` column present but empty

### API
- `GET /api/news?key={keyword}` — single endpoint, works for any keyword (company symbol, sector, custom)
- On-demand fetch — if keyword not in DB, fetches from NSE and Google RSS immediately and returns result
- Returns consistent empty response `{ news: [] }` when nothing found, never returns 404

### Git
- Code pushed to `nse-news-scheduler` branch on GitHub
- Pull Request raised against `develop` branch

---

## ❌ Pending or TO DO

### Samll Changes (dependency on Sector table and Global Watchlist table)
- [ ] Change the Sector table name and column name once the Sector Table is created
- [ ] Change the Global Watchlist table name and column name once the Global Watchlist Table is created

### Multithreading Improvements
- [ ] NSE dedicated named thread — currently uses Spring's default scheduler thread, needs an explicitly configured dedicated thread
- [ ] `UrlWindowService` thread safety — `HashSet` and `Queue` are accessed by 2 Google RSS threads simultaneously, needs `synchronized` methods or `ConcurrentLinkedQueue` + `Collections.synchronizedSet` to prevent race conditions
- [ ] `WorkerService` same-keyword race condition — if 2 threads process the same keyword simultaneously, `findByKeyword` → `save` sequence can conflict, needs handling

### Duplicate Detection Improvements
- [ ] Cross-source headline similarity check — if NSE and Google RSS both save news about the same event, currently stored as two separate items. Need headline similarity >= 60% threshold (Levenshtein or Jaccard algorithm) to detect and skip cross-source duplicates

### Features
- [ ] New watchlist company immediate fetch — when a user adds a new company to their watchlist mid-day, Google RSS should fetch news for it immediately, not wait until 8 AM next day. Needs a trigger from `watchlist-service` to `nse-news-scheduler`

---

## Database Schema

```sql
CREATE TABLE company_news (
    id           BIGSERIAL PRIMARY KEY,
    keyword      VARCHAR(255) UNIQUE NOT NULL,
    sentiments   VARCHAR(255),                  -- empty for now
    news         JSONB,                         -- [{date, summary, link}, ...]
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## Key Design Decisions

| Decision | Reason |
|---|---|
| One row per keyword | Simple, fast — one SELECT gives everything for a company |
| JSONB for news array | Variable number of items per company, no need for a separate news table |
| `seq_id` NOT stored in DB | Lives only in in-memory window — reduces DB size, faster writes |
| URL NOT stored in DB | Same reason — only in memory for dedup |
| Same `WorkerService` for NSE and Google RSS | Both sources write to the same table in the same format — no duplication of save logic |
| `news.limit` in properties | Business team can change limits without touching code or rebuilding JAR |
