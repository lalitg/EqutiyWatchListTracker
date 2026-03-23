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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

/**
 * Service that fetches corporate announcements from the NSE (National Stock Exchange) API.
 *
 * <p>Applies Resilience4j's {@link CircuitBreaker} and {@link Retry} to all outbound HTTP calls:
 * <ul>
 *   <li><b>Circuit Breaker (outer)</b> — if the circuit is already OPEN (too many recent failures),
 *       short-circuits immediately and returns the fallback — no HTTP call is attempted.</li>
 *   <li><b>Retry (inner)</b> — only runs when the circuit is CLOSED or HALF-OPEN. On failure,
 *       waits 1 second and retries up to 3 times before the circuit breaker counts the final
 *       attempt as a failure.</li>
 * </ul>
 *
 * <p>Micrometer metrics are recorded for every fetch attempt (including retries):
 * <ul>
 *   <li>{@code news.fetch.duration} tagged {@code source=nse} — wall-clock time of each attempt.</li>
 *   <li>{@code news.items.fetched} tagged {@code source=nse} — count of announcements per success.</li>
 * </ul>
 */
@Service
public class NseFetcher {

    private static final Logger log = LogManager.getLogger(NseFetcher.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final int requestTimeoutSeconds;
    private final String nseUrl;
    private final MeterRegistry meterRegistry;

    /**
     * Timer recording the wall-clock time of each NSE HTTP fetch + JSON parse attempt.
     * Visible at {@code /actuator/metrics/news.fetch.duration?tag=source:nse}.
     * Used to detect degradation (p99 latency rising) before the circuit breaker opens.
     */
    private final Timer fetchTimer;

    /**
     * Counter incremented with the number of announcements returned from each successful parse.
     * Useful for alerting on "0 announcements fetched for N minutes" (silent NSE feed detection).
     */
    private final Counter fetchedCounter;

    /**
     * Constructs an {@code NseFetcher} with all required dependencies injected by Spring.
     *
     * @param objectMapper          Jackson mapper for parsing the NSE JSON response
     * @param httpClient            shared singleton HTTP client with connection pooling
     * @param requestTimeoutSeconds per-request read timeout (from {@code news.http.request-timeout-seconds})
     * @param nseUrl                NSE API endpoint URL (from {@code news.nse.url})
     * @param meterRegistry         Micrometer registry for registering fetch metrics
     */
    public NseFetcher(ObjectMapper objectMapper,
                      HttpClient httpClient,
                      @Value("${news.http.request-timeout-seconds:10}") int requestTimeoutSeconds,
                      @Value("${news.nse.url}") String nseUrl,
                      MeterRegistry meterRegistry) {
        this.objectMapper          = objectMapper;
        this.httpClient            = httpClient;
        this.requestTimeoutSeconds = requestTimeoutSeconds;
        this.nseUrl                = nseUrl;
        this.meterRegistry         = meterRegistry;
        this.fetchTimer = Timer.builder("news.fetch.duration")
            .tag("source", "nse")
            .description("Time taken for NSE HTTP fetch and JSON parse per attempt")
            .register(meterRegistry);
        this.fetchedCounter = Counter.builder("news.items.fetched")
            .tag("source", "nse")
            .description("Total NSE announcements retrieved per successful fetch")
            .register(meterRegistry);
    }

    /**
     * Fetches all corporate announcements from the NSE equities endpoint.
     *
     * <p>Resilience4j ordering — {@code @CircuitBreaker} wraps {@code @Retry} (outer-to-inner):
     * <ul>
     *   <li>{@code @CircuitBreaker} is the outer guard. If the circuit is OPEN, it returns
     *       the fallback immediately — no HTTP call attempted, no retry consumed.</li>
     *   <li>{@code @Retry} is the inner guard. On {@link IOException} (network failure, timeout),
     *       waits 1s and retries up to 3 times before the circuit breaker counts the failure.</li>
     * </ul>
     *
     * <p>{@link IOException} is re-wrapped as {@link RuntimeException} so it propagates through
     * the Resilience4j AOP proxy (which only intercepts unchecked exceptions by default).
     * {@link InterruptedException} is handled inline — do not retry when the thread is being killed.
     *
     * <p>The {@link Timer.Sample} is started before the HTTP call and stopped in {@code finally}
     * so every code path (success, non-200, exception) contributes a timing observation.
     * On retry, each individual attempt records its own timer entry.
     *
     * @return a list of {@link NseAnnouncement} objects parsed from the NSE response;
     *         returns an empty list if interrupted or if the fallback fires
     */
    @CircuitBreaker(name = "nse-fetch", fallbackMethod = "fetchFallback")
    @Retry(name = "nse-fetch")
    public List<NseAnnouncement> fetch() {
        Timer.Sample sample = Timer.start(meterRegistry);
        log.debug("Starting NSE HTTP fetch — url: {}", nseUrl);
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
                // Throw RuntimeException so Resilience4j counts this as a failure and retries
                throw new RuntimeException("NSE API returned non-200 status: " + response.statusCode());
            }

            return parseAnnouncements(response.body());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // restore interrupt flag — do not retry
            log.warn("NSE fetch interrupted — thread is shutting down");
            return List.of();
        } catch (IOException e) {
            // Wrap as RuntimeException so Resilience4j @Retry intercepts it
            throw new RuntimeException("NSE HTTP fetch failed: " + e.getMessage(), e);
        } finally {
            sample.stop(fetchTimer);
        }
    }

    /**
     * Resilience4j fallback invoked when all retries are exhausted or the circuit is open.
     *
     * <p>Returns an empty list so the scheduler gracefully skips this cycle.
     * The next scheduled run (15 minutes later) will attempt the fetch again.
     *
     * @param ex the exception that triggered the fallback (last retry failure or circuit open cause)
     * @return an empty immutable list
     */
    @SuppressWarnings("unused") // called by Resilience4j via AOP reflection — not referenced directly
    private List<NseAnnouncement> fetchFallback(Throwable ex) {
        log.error("NSE fetch unavailable — retries exhausted or circuit open. Cause: {}", ex.getMessage());
        return List.of();
    }

    /**
     * Parses the NSE JSON response body into a list of {@link NseAnnouncement} objects.
     *
     * <p>JSON parse errors are caught here — they represent bad/unexpected data from NSE,
     * not a retriable network failure, so they do not propagate to the retry logic.
     * Each successfully parsed announcement increments {@link #fetchedCounter}.
     *
     * @param json the raw JSON string from the NSE API response body
     * @return a list of parsed {@link NseAnnouncement} objects; empty if parsing fails entirely
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
                    log.warn("Could not parse seqId value [{}] for symbol [{}] — skipping", seqStr, symbol);
                    continue;
                }

                String labelledText = (text != null) ? text + " - NSE" : null;
                NewsItem item = new NewsItem(anDt, labelledText, file);
                item.setSymbol(symbol);
                result.add(new NseAnnouncement(item, seqId));
            }

            fetchedCounter.increment(result.size());
            log.info("NSE JSON parsed successfully — {} announcements retrieved", result.size());
        } catch (Exception e) {
            log.error("Failed to parse NSE JSON response: {}", e.getMessage(), e);
        }
        return result;
    }

    /**
     * Safely extracts a string value from a JSON object map.
     *
     * @param map the parsed JSON object
     * @param key the field name to look up
     * @return the string representation of the value, or {@code null} if the key is absent
     */
    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : null;
    }
}
