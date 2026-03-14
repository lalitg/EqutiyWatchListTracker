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
            String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
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
}
