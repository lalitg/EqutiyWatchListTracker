package com.companynews.newsscheduler.service;

import com.companynews.newsscheduler.dto.NewsItem;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Service that fetches news articles from Google RSS feeds for a given keyword.
 *
 * <p>Each call to {@link #fetch(String)} performs:
 * <ol>
 *   <li>Query enrichment — appends finance-specific suffixes based on the keyword type
 *       (company symbol, sector, or macro term) to improve Google RSS result relevance.</li>
 *   <li>HTTP GET to the Google RSS endpoint with the enriched, URL-encoded keyword.</li>
 *   <li>XXE-safe XML parsing of the RSS response into a list of {@link NewsItem} objects.</li>
 * </ol>
 *
 * <p>Applies the same Resilience4j {@link CircuitBreaker} + {@link Retry} strategy as
 * {@link NseFetcher}: circuit breaker is the outer guard, retry is the inner guard.
 *
 * <p>Micrometer metrics:
 * <ul>
 *   <li>{@code news.fetch.duration} tagged {@code source=rss} — wall-clock time per fetch attempt.</li>
 *   <li>{@code news.items.fetched} tagged {@code source=rss} — count of items per successful parse.</li>
 * </ul>
 */
@Service
public class RssFetcher {

    private static final Logger log = LogManager.getLogger(RssFetcher.class);

    /**
     * Known sector display names used to detect sector-type keywords and apply the correct
     * Google RSS query enrichment suffix. Declared as a {@code static final} field to avoid
     * re-allocating the set on every call to {@link #isSector(String)}.
     */
    private static final Set<String> KNOWN_SECTORS = Set.of(
        "INFORMATION TECHNOLOGY", "BANKING", "PHARMACEUTICALS",
        "AUTOMOBILE", "FMCG", "ENERGY", "INFRASTRUCTURE",
        "CHEMICALS", "METALS", "REAL ESTATE", "TELECOM",
        "HEALTHCARE", "FINANCE", "INSURANCE", "MEDIA"
    );

    private final HttpClient httpClient;
    private final int requestTimeoutSeconds;
    private final String rssBaseUrl;
    private final MeterRegistry meterRegistry;

    /** Maximum number of RSS items to return per keyword fetch. Configurable via {@code news.limit}. */
    @Value("${news.limit:5}")
    private int newsLimit;

    /**
     * Timer recording the wall-clock time of each Google RSS HTTP fetch + XML parse per keyword.
     * Visible at {@code /actuator/metrics/news.fetch.duration?tag=source:rss}.
     */
    private final Timer fetchTimer;

    /**
     * Counter incremented with the number of items returned from each successful XML parse.
     * Useful for detecting consistently empty results per keyword.
     */
    private final Counter fetchedCounter;

    /**
     * Constructs an {@code RssFetcher} with all required dependencies injected by Spring.
     *
     * @param httpClient            shared singleton HTTP client with connection pooling
     * @param requestTimeoutSeconds per-request read timeout (from {@code news.http.request-timeout-seconds})
     * @param rssBaseUrl            Google RSS base URL (from {@code news.rss.base-url})
     * @param meterRegistry         Micrometer registry for registering fetch metrics
     */
    public RssFetcher(HttpClient httpClient,
                      @Value("${news.http.request-timeout-seconds:10}") int requestTimeoutSeconds,
                      @Value("${news.rss.base-url}") String rssBaseUrl,
                      MeterRegistry meterRegistry) {
        this.httpClient            = httpClient;
        this.requestTimeoutSeconds = requestTimeoutSeconds;
        this.rssBaseUrl            = rssBaseUrl;
        this.meterRegistry         = meterRegistry;
        this.fetchTimer = Timer.builder("news.fetch.duration")
            .tag("source", "rss")
            .description("Time taken for Google RSS HTTP fetch and XML parse per keyword")
            .register(meterRegistry);
        this.fetchedCounter = Counter.builder("news.items.fetched")
            .tag("source", "rss")
            .description("Total RSS items retrieved per successful fetch")
            .register(meterRegistry);
    }

    /**
     * Fetches Google RSS news articles for the given keyword.
     *
     * <p>Resilience4j ordering — {@code @CircuitBreaker} (outer) wraps {@code @Retry} (inner):
     * <ul>
     *   <li>If the circuit is OPEN, the fallback is returned immediately — no HTTP call made.</li>
     *   <li>If the circuit is CLOSED, {@link IOException} triggers a retry (up to 3 attempts, 1s wait).</li>
     * </ul>
     *
     * <p>The fallback method signature is {@code fetchFallback(String keyword, Throwable ex)}
     * because Resilience4j requires the fallback to match the annotated method's parameter list
     * plus a trailing {@link Throwable}.
     *
     * <p>The {@link Timer.Sample} is started before the HTTP call and stopped in {@code finally}
     * so every code path contributes a timing observation.
     *
     * @param keyword the keyword to search for (will be enriched before URL encoding)
     * @return a list of {@link NewsItem} objects parsed from the RSS feed;
     *         returns an empty list if interrupted or if the fallback fires
     */
    @CircuitBreaker(name = "rss-fetch", fallbackMethod = "fetchFallback")
    @Retry(name = "rss-fetch")
    public List<NewsItem> fetch(String keyword) {
        Timer.Sample sample = Timer.start(meterRegistry);
        log.debug("Starting Google RSS fetch for keyword: {}", keyword);
        try {
            String enrichedKeyword = enrichQuery(keyword);
            String encodedKeyword  = URLEncoder.encode(enrichedKeyword, StandardCharsets.UTF_8);
            String url             = rssBaseUrl + encodedKeyword;

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                .header("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/120.0.0.0 Safari/537.36")
                .GET()
                .build();

            HttpResponse<InputStream> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                throw new RuntimeException(
                    "Google RSS returned non-200 status " + response.statusCode() +
                    " for keyword: " + keyword);
            }

            return parseRss(response.body(), keyword);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("RSS fetch interrupted for keyword: {}", keyword);
            return List.of();
        } catch (IOException e) {
            throw new RuntimeException(
                "RSS HTTP fetch failed for keyword [" + keyword + "]: " + e.getMessage(), e);
        } finally {
            sample.stop(fetchTimer);
        }
    }

    /**
     * Resilience4j fallback invoked when all retries are exhausted or the circuit is open.
     *
     * <p>Returns an empty list so the calling scheduler skips this keyword gracefully.
     * The next scheduled run will attempt the fetch again.
     *
     * @param keyword the keyword that failed
     * @param ex      the exception that triggered the fallback
     * @return an empty immutable list
     */
    @SuppressWarnings("unused") // called by Resilience4j via AOP reflection — not referenced directly
    private List<NewsItem> fetchFallback(String keyword, Throwable ex) {
        log.error("RSS fetch unavailable for keyword [{}] — retries exhausted or circuit open. Cause: {}",
                  keyword, ex.getMessage());
        return List.of();
    }

    /**
     * Parses a Google RSS XML {@link InputStream} into a list of {@link NewsItem} objects.
     *
     * <p>WHY XXE protection (XML External Entity injection):
     * An attacker who can influence the RSS response (MITM, compromised upstream) could inject
     * an XML DOCTYPE with an external entity reference, causing the parser to read arbitrary
     * files from the server filesystem or make outbound connections. Disabling DOCTYPE
     * declarations entirely eliminates this entire attack class.
     * {@code setFeature("disallow-doctype-decl", true)} is the recommended single-line XXE defence.
     *
     * <p>XML parse errors are caught here — they represent data errors, not retriable network
     * failures, so they do not propagate to the retry logic.
     *
     * @param body    the RSS XML input stream from the HTTP response
     * @param keyword the keyword for which this feed was fetched (used for logging)
     * @return a list of up to {@code news.limit} {@link NewsItem} objects parsed from the feed;
     *         empty if parsing fails entirely
     */
    private List<NewsItem> parseRss(InputStream body, String keyword) {
        List<NewsItem> result = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // XXE prevention — disable DOCTYPE and all external entity processing
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(body);
            doc.getDocumentElement().normalize();

            NodeList items = doc.getElementsByTagName("item");
            int count = 0;
            for (int i = 0; i < items.getLength() && count < newsLimit; i++) {
                Element item   = (Element) items.item(i);
                String title   = getTagValue("title",   item);
                String link    = getTagValue("link",    item);
                String pubDate = getTagValue("pubDate", item);

                if (link == null || link.isEmpty()) continue;

                result.add(new NewsItem(pubDate, title, link));
                count++;
            }

            fetchedCounter.increment(result.size());
            log.info("Google RSS parsed {} items for keyword: {}", result.size(), keyword);
        } catch (Exception e) {
            log.error("RSS XML parsing failed for keyword [{}]: {}", keyword, e.getMessage(), e);
        }
        return result;
    }

    /**
     * Extracts the text content of the first occurrence of a named XML tag within an element.
     *
     * @param tagName the XML tag name to look for
     * @param element the parent element to search within
     * @return the text content of the first matching tag, or {@code null} if not found
     */
    private String getTagValue(String tagName, Element element) {
        NodeList list = element.getElementsByTagName(tagName);
        if (list.getLength() == 0) return null;
        return list.item(0).getTextContent();
    }

    /**
     * Enriches a search keyword with finance-specific terms to improve Google RSS relevance.
     *
     * <p>Enrichment strategy:
     * <ul>
     *   <li><b>Company symbols</b> (2–10 uppercase alphanumeric chars, no spaces, e.g., {@code INFY})
     *       — appends {@code "NSE stock"} to return stock-specific news.</li>
     *   <li><b>Sector names</b> (known sector or contains a space, e.g., {@code Banking})
     *       — appends {@code "sector India stock market"}.</li>
     *   <li><b>Everything else</b> (macro/geopolitical terms, e.g., {@code RBI monetary policy})
     *       — appends {@code "finance economy market India"}.</li>
     * </ul>
     *
     * @param keyword the raw keyword to enrich; returned unchanged if null or blank
     * @return the enriched query string ready for URL encoding
     */
    private String enrichQuery(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) return keyword;

        String upper = keyword.trim().toUpperCase();

        // Company symbols: 2–10 uppercase letters/digits, no spaces
        if (upper.matches("[A-Z0-9]{2,10}") && !upper.contains(" ")) {
            return keyword + " NSE stock";
        }

        if (isSector(upper)) {
            return keyword + " sector India stock market";
        }

        return keyword + " finance economy market India";
    }

    /**
     * Determines whether a keyword (already uppercased) represents a market sector.
     *
     * <p>Uses the pre-allocated {@link #KNOWN_SECTORS} set for O(1) lookup.
     * Also treats any keyword containing a space as a sector/phrase rather than a symbol.
     *
     * @param upperKeyword the keyword in uppercase
     * @return {@code true} if the keyword is a known sector or a multi-word phrase
     */
    private boolean isSector(String upperKeyword) {
        return KNOWN_SECTORS.contains(upperKeyword) || upperKeyword.contains(" ");
    }
}
