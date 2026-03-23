package com.companynews.newsscheduler.service;

import com.companynews.newsscheduler.dto.NewsItem;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

@Service
public class RssFetcher {

    private static final Logger log = LoggerFactory.getLogger(RssFetcher.class);

    private final HttpClient httpClient;
    private final int requestTimeoutSeconds;
    private final String rssBaseUrl;

    @Value("${news.limit:5}")
    private int newsLimit;

    /**
     * Micrometer metrics:
     *
     * news.fetch.duration (source=rss) — Timer recording the wall-clock time of each
     *   Google RSS HTTP call + XML parse for a single keyword.
     *   Visible at /actuator/metrics/news.fetch.duration?tag=source:rss
     *
     * news.items.fetched (source=rss) — Counter incremented with the number of
     *   items returned from a successful XML parse.
     */
    private final MeterRegistry meterRegistry;
    private final Timer fetchTimer;
    private final Counter fetchedCounter;

    public RssFetcher(HttpClient httpClient,
                      @Value("${news.http.request-timeout-seconds:10}") int requestTimeoutSeconds,
                      @Value("${news.rss.base-url}") String rssBaseUrl,
                      MeterRegistry meterRegistry) {
        this.httpClient = httpClient;
        this.requestTimeoutSeconds = requestTimeoutSeconds;
        this.rssBaseUrl = rssBaseUrl;
        this.meterRegistry = meterRegistry;
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
     * Fetches Google RSS news for a single keyword.
     *
     * Same resilience4j strategy as NseFetcher:
     * @CircuitBreaker (outer) — if Google RSS is repeatedly unreachable, opens the circuit
     *   for 60s so we stop submitting tasks that will all fail immediately.
     * @Retry (inner) — retries up to 3 times on IOException before circuit counts the failure.
     *
     * The fallback takes (String keyword, Throwable ex) because the annotated method
     * takes a String parameter — resilience4j requires the fallback to match the method
     * signature plus a trailing Throwable.
     *
     * Timer.Sample is started at the top and stopped in finally so every code path
     * (success, non-200 status, IOException) contributes a timing observation.
     */
    @CircuitBreaker(name = "rss-fetch", fallbackMethod = "fetchFallback")
    @Retry(name = "rss-fetch")
    public List<NewsItem> fetch(String keyword) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            String enrichedKeyword = enrichQuery(keyword);
            String encodedKeyword = URLEncoder.encode(enrichedKeyword, StandardCharsets.UTF_8);
            String url = rssBaseUrl + encodedKeyword;

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
                    "Google RSS returned status " + response.statusCode() + " for: " + keyword);
            }

            return parseRss(response.body(), keyword);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("RSS fetch interrupted for keyword: {}", keyword);
            return List.of();
        } catch (IOException e) {
            throw new RuntimeException("RSS fetch failed for keyword '" + keyword + "': " + e.getMessage(), e);
        } finally {
            sample.stop(fetchTimer);
        }
    }

    /** Fallback called when retries are exhausted or circuit is open. */
    @SuppressWarnings("unused") // called by resilience4j via AOP reflection
    private List<NewsItem> fetchFallback(String keyword, Throwable ex) {
        log.error("RSS fetch unavailable for '{}' — retries exhausted or circuit open: {}",
                  keyword, ex.getMessage());
        return List.of();
    }

    /**
     * Parses the RSS XML InputStream into a list of NewsItem objects.
     *
     * WHY XXE protection (XML External Entity):
     * An attacker who can influence the RSS response (MITM, compromised upstream)
     * could inject an XML DOCTYPE with an external entity reference, causing the parser
     * to read arbitrary files from the server filesystem or make outbound connections.
     * Disabling DOCTYPE declarations entirely eliminates the entire class of XXE attacks.
     * setFeature("disallow-doctype-decl") is the recommended single-line XXE defence.
     *
     * XML parse errors are caught here — these are data errors, not retriable network failures.
     * Increments fetchedCounter with the number of items successfully parsed.
     */
    private List<NewsItem> parseRss(InputStream body, String keyword) {
        List<NewsItem> result = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // XXE prevention — disable DOCTYPE and external entity processing
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
                Element item = (Element) items.item(i);
                String title   = getTagValue("title",   item);
                String link    = getTagValue("link",    item);
                String pubDate = getTagValue("pubDate", item);

                if (link == null || link.isEmpty()) continue;

                result.add(new NewsItem(pubDate, title, link));
                count++;
            }

            fetchedCounter.increment(result.size());
            log.info("Google RSS fetched {} items for keyword: {}", result.size(), keyword);
        } catch (Exception e) {
            log.error("RSS XML parsing failed for keyword '{}': {}", keyword, e.getMessage());
        }
        return result;
    }

    private String getTagValue(String tagName, Element element) {
        NodeList list = element.getElementsByTagName(tagName);
        if (list.getLength() == 0) return null;
        return list.item(0).getTextContent();
    }

    /**
     * Enriches a search keyword with finance-related terms for better Google RSS results.
     *
     * Strategy:
     * - Company symbols (INFY, TCS) — add "NSE stock" → stock-specific news
     * - Sector names (Banking, Pharma) — add "sector India stock market"
     * - Everything else (macro, custom) — add "finance economy market India"
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

    private boolean isSector(String keyword) {
        Set<String> knownSectors = Set.of(
            "INFORMATION TECHNOLOGY", "BANKING", "PHARMACEUTICALS",
            "AUTOMOBILE", "FMCG", "ENERGY", "INFRASTRUCTURE",
            "CHEMICALS", "METALS", "REAL ESTATE", "TELECOM",
            "HEALTHCARE", "FINANCE", "INSURANCE", "MEDIA"
        );
        return knownSectors.contains(keyword) || keyword.contains(" ");
    }
}
