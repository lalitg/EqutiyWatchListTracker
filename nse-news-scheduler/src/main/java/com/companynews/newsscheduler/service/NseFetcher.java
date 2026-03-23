package com.companynews.newsscheduler.service;

import com.companynews.newsscheduler.dto.NseAnnouncement;
import com.companynews.newsscheduler.dto.NewsItem;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class NseFetcher {

    private static final Logger log = LoggerFactory.getLogger(NseFetcher.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final int requestTimeoutSeconds;
    private final String nseUrl;

    /**
     * Micrometer metrics:
     *
     * news.fetch.duration (source=nse) — Timer recording the wall-clock time of each
     *   NSE HTTP call + JSON parse. Recorded on every attempt including retries.
     *   Visible at /actuator/metrics/news.fetch.duration?tag=source:nse
     *   Used to detect degradation (p99 latency rising) before circuit breaker opens.
     *
     * news.items.fetched (source=nse) — Counter incremented with the number of
     *   announcements returned from a successful parse.
     *   Useful for alerting on "0 announcements for N minutes" (NSE feed silent).
     */
    private final MeterRegistry meterRegistry;
    private final Timer fetchTimer;
    private final Counter fetchedCounter;

    public NseFetcher(ObjectMapper objectMapper,
                      HttpClient httpClient,
                      @Value("${news.http.request-timeout-seconds:10}") int requestTimeoutSeconds,
                      @Value("${news.nse.url}") String nseUrl,
                      MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.requestTimeoutSeconds = requestTimeoutSeconds;
        this.nseUrl = nseUrl;
        this.meterRegistry = meterRegistry;
        this.fetchTimer = Timer.builder("news.fetch.duration")
            .tag("source", "nse")
            .description("Time taken for NSE HTTP fetch and JSON parse")
            .register(meterRegistry);
        this.fetchedCounter = Counter.builder("news.items.fetched")
            .tag("source", "nse")
            .description("Total NSE announcements retrieved per successful fetch")
            .register(meterRegistry);
    }

    /**
     * Fetches all corporate announcements from NSE.
     *
     * WHY @CircuitBreaker wraps @Retry (outer-to-inner):
     * @CircuitBreaker is the outer guard. If the circuit is already OPEN (too many
     * recent failures), it short-circuits immediately and returns the fallback — no
     * HTTP call attempted, no retry tried. This prevents a storm of retry attempts
     * hitting an already-dead NSE endpoint.
     *
     * @Retry is the inner guard. Only runs when the circuit is CLOSED or HALF-OPEN.
     * On IOException (network failure, timeout), it waits 1s and retries up to 3 times
     * before the circuit breaker counts the final attempt as a failure.
     *
     * Both annotations point to the same fallback — whichever fires last
     * (exhausted retries OR open circuit) returns an empty list.
     *
     * IOException is wrapped as RuntimeException so it propagates through the
     * resilience4j proxy. InterruptedException is handled inline (do not retry
     * on interrupt — the thread is being killed).
     *
     * Timer.Sample is started at the top and stopped in finally so every code path
     * (success, non-200 status, IOException) contributes a timing observation.
     * On retry, each individual attempt records its own timer entry.
     */
    @CircuitBreaker(name = "nse-fetch", fallbackMethod = "fetchFallback")
    @Retry(name = "nse-fetch")
    public List<NseAnnouncement> fetch() {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(nseUrl))
                .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                .header("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "application/json")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Referer", "https://www.nseindia.com")
                .GET()
                .build();

            HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                // Throw RuntimeException so resilience4j counts this as a failure and retries
                throw new RuntimeException("NSE API returned status: " + response.statusCode());
            }

            return parseAnnouncements(response.body());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // restore interrupt flag — do not retry
            log.warn("NSE fetch interrupted");
            return List.of();
        } catch (IOException e) {
            // Wrap as RuntimeException so resilience4j @Retry intercepts it
            throw new RuntimeException("NSE fetch failed: " + e.getMessage(), e);
        } finally {
            sample.stop(fetchTimer);
        }
    }

    /**
     * Fallback called by resilience4j when all retries are exhausted OR the circuit is open.
     * Returns empty list — the next scheduled run (15 min) will try again.
     */
    @SuppressWarnings("unused") // called by resilience4j via AOP reflection
    private List<NseAnnouncement> fetchFallback(Throwable ex) {
        log.error("NSE fetch unavailable — retries exhausted or circuit open: {}", ex.getMessage());
        return List.of();
    }

    /**
     * Parses the NSE JSON response body into a list of NseAnnouncement objects.
     * JSON parse errors are caught here (non-retriable — bad data, not network failure).
     * Increments fetchedCounter with the number of valid announcements parsed.
     */
    private List<NseAnnouncement> parseAnnouncements(String json) {
        List<NseAnnouncement> result = new ArrayList<>();
        try {
            List<Map<String, Object>> announcements = objectMapper.readValue(
                json, new TypeReference<List<Map<String, Object>>>() {});

            for (Map<String, Object> a : announcements) {
                String symbol = getString(a, "symbol");
                String anDt   = getString(a, "an_dt");
                String text   = getString(a, "attchmntText");
                String file   = getString(a, "attchmntFile");
                String seqStr = getString(a, "seq_id");

                if (symbol == null || seqStr == null) continue;

                Long seqId;
                try {
                    seqId = Long.parseLong(seqStr);
                } catch (NumberFormatException e) {
                    log.warn("Could not parse seqId: {} — skipping", seqStr);
                    continue;
                }

                String labelledText = (text != null) ? text + " - NSE" : null;
                NewsItem item = new NewsItem(anDt, labelledText, file);
                item.setSymbol(symbol);
                result.add(new NseAnnouncement(item, seqId));
            }

            fetchedCounter.increment(result.size());
            log.info("NSE fetch complete — {} announcements retrieved", result.size());
        } catch (Exception e) {
            log.error("NSE response parsing failed: {}", e.getMessage());
        }
        return result;
    }

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }
}
