package com.companynews.newsscheduler.service;

import com.companynews.newsscheduler.dto.NewsItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class GoogleRssService {

    private static final Logger log = LoggerFactory.getLogger(GoogleRssService.class);

    // Base URL for Google News RSS — Indian edition
    private static final String GOOGLE_RSS_BASE =
        "https://news.google.com/rss/search?hl=en-IN&gl=IN&ceid=IN:en&q=";

    @Value("${news.limit:5}")
    private int newsLimit;

    /**
     * Fetches Google RSS news for a single keyword.
     * Parses the XML RSS feed and returns a list of NewsItem objects.
     *
     * Google RSS is XML format — looks like:
     * <rss>
     *   <channel>
     *     <item>
     *       <title>Infosys Q3 results...</title>
     *       <link>https://...</link>
     *       <pubDate>Thu, 12 Mar 2026 10:00:00 GMT</pubDate>
     *     </item>
     *   </channel>
     * </rss>
     *
     * @param keyword the keyword to search for e.g. "INFY", "Banking", "Nifty 50"
     * @return list of NewsItem parsed from the RSS feed
     */
    public List<NewsItem> fetchNews(String keyword) {
        List<NewsItem> result = new ArrayList<>();

        try {
            // Build the URL — URLEncoder handles spaces and special characters
            // e.g. "Nifty 50" becomes "Nifty+50" in the URL
            // Enrich the query with finance-related terms
            // WHY: Generic keywords like "US" or "Europe" return general news
            // (sports, politics, entertainment). Adding finance terms forces
            // Google to return only financially relevant articles.
            // WHY these specific terms:
            // "finance" — filters for financial news
            // "market"  — stock/commodity market news
            // "economy" — macroeconomic news that affects markets
            // We do NOT add these for company symbols (INFY, RELIANCE etc)
            // because company symbols are already specific enough.
            String enrichedKeyword = enrichQuery(keyword);
            String encodedKeyword = URLEncoder.encode(enrichedKeyword, StandardCharsets.UTF_8);
            String url = GOOGLE_RSS_BASE + encodedKeyword;

            // Make the HTTP request
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/120.0.0.0 Safari/537.36")
                .GET()
                .build();

            HttpResponse<InputStream> response =
                client.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                log.warn("Google RSS returned status {} for keyword: {}",
                         response.statusCode(), keyword);
                return result;
            }

            // Parse the XML RSS response
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(response.body());
            doc.getDocumentElement().normalize();

            // Get all <item> elements — each one is a news article
            NodeList items = doc.getElementsByTagName("item");

            int count = 0;
            for (int i = 0; i < items.getLength() && count < newsLimit; i++) {
                Element item = (Element) items.item(i);

                // Extract the three fields we care about
                String title   = getTagValue("title",   item);
                String link    = getTagValue("link",    item);
                String pubDate = getTagValue("pubDate", item);

                // Skip items with no link — link is our dedup key
                if (link == null || link.isEmpty()) continue;

                NewsItem newsItem = new NewsItem(pubDate, title, link);
                result.add(newsItem);
                count++;
            }

            log.info("Google RSS fetched {} items for keyword: {}", result.size(), keyword);

        } catch (Exception e) {
            log.error("Error fetching Google RSS for keyword '{}': {}",
                      keyword, e.getMessage());
        }

        return result;
    }

    /**
     * Helper — extracts text content of a named XML tag from an element.
     * e.g. getTagValue("title", item) returns the text inside <title>...</title>
     */
    private String getTagValue(String tagName, Element element) {
        NodeList list = element.getElementsByTagName(tagName);
        if (list.getLength() == 0) return null;
        return list.item(0).getTextContent();
    }

    /**
     * Enriches a search keyword with finance-related terms for better results.
     *
     * Strategy:
     * - Company symbols (INFY, RELIANCE, TCS) — already specific, just add "NSE"
     *   to get stock-related news instead of general company news
     * - Sector names (Banking, Pharma) — add "sector India stock market"
     * - Region/macro keywords (US, Europe, crude oil) — add "finance economy market"
     * - Custom keywords from keywords.txt — add "India finance"
     *
     * WHY different enrichment per keyword type:
     * "INFY finance" returns Infosys financial news
     * "Banking sector India stock market" returns Indian banking sector news
     * "US finance economy market" returns US economic news affecting markets
     */
    private String enrichQuery(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return keyword;
        }

        String upper = keyword.trim().toUpperCase();

        // Company symbols — typically 2-10 uppercase characters, no spaces
        // Examples: INFY, RELIANCE, TCS, HDFCBANK, BAJFINANCE
        // Add "NSE stock" to get stock-specific news
        if (upper.matches("[A-Z0-9]{2,10}") && !upper.contains(" ")) {
            return keyword + " NSE stock";
        }

        // Sector names — contain spaces or are longer descriptive words
        // Examples: "Information Technology", "Banking", "Pharmaceuticals"
        // Add "sector India stock market" for sector-specific market news
        if (isSector(upper)) {
            return keyword + " sector India stock market";
        }

        // Everything else — regions, macro keywords, custom keywords
        // Examples: "US", "Europe", "crude oil", "Nifty 50", "RBI"
        // Add "finance economy market India" for financial context
        return keyword + " finance economy market India";
    }

    /**
     * Checks if a keyword looks like a sector name.
     * Sector names are typically multi-word or known financial sector terms.
     */
    private boolean isSector(String keyword) {
        // List of known sector keywords — matches what's in your sectors table
        java.util.Set<String> knownSectors = java.util.Set.of(
            "INFORMATION TECHNOLOGY", "BANKING", "PHARMACEUTICALS",
            "AUTOMOBILE", "FMCG", "ENERGY", "INFRASTRUCTURE",
            "CHEMICALS", "METALS", "REAL ESTATE", "TELECOM",
            "HEALTHCARE", "FINANCE", "INSURANCE", "MEDIA"
        );
        return knownSectors.contains(keyword) || keyword.contains(" ");
    }
}
