package com.watchlist.global.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.watchlist.global.model.GlobalIndexEntry;
import com.watchlist.global.model.GlobalIndexEntry.Region;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class YahooFinanceClient {

    private static final Logger logger = LogManager.getLogger(YahooFinanceClient.class);

    private static final String YAHOO_URL =
        "https://query1.finance.yahoo.com/v7/finance/quote?symbols=" +
        "%5EDJI,%5EGSPC,%5EIXIC,%5EFTSE,%5EFCHI,%5EGDAXI,%5EN225,%5EHSI,%5ESTI,%5EKS11";

    private final HttpClient   httpClient   = HttpClient.newBuilder().followRedirects(Redirect.ALWAYS).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // symbol → (display name, flag emoji, region)
    private static final Map<String, Object[]> INDEX_META = Map.of(
        "^DJI",   new Object[]{"Dow Jones",    "🇺🇸", Region.US_MARKETS},
        "^GSPC",  new Object[]{"S&P 500",      "🇺🇸", Region.US_MARKETS},
        "^IXIC",  new Object[]{"Nasdaq",        "🇺🇸", Region.US_MARKETS},
        "^FTSE",  new Object[]{"FTSE 100",      "🇬🇧", Region.EUROPEAN_MARKETS},
        "^FCHI",  new Object[]{"CAC 40",        "🇫🇷", Region.EUROPEAN_MARKETS},
        "^GDAXI", new Object[]{"DAX",           "🇩🇪", Region.EUROPEAN_MARKETS},
        "^N225",  new Object[]{"Nikkei 225",    "🇯🇵", Region.ASIAN_MARKETS},
        "^HSI",   new Object[]{"Hang Seng",     "🇭🇰", Region.ASIAN_MARKETS},
        "^STI",   new Object[]{"Straits Times", "🇸🇬", Region.ASIAN_MARKETS},
        "^KS11",  new Object[]{"Kospi",         "🇰🇷", Region.ASIAN_MARKETS}
    );

    public List<GlobalIndexEntry> fetchAll() {
        List<GlobalIndexEntry> result = new ArrayList<>();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(YAHOO_URL))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode items = root.path("quoteResponse").path("result");

            for (JsonNode item : items) {
                String symbol = item.path("symbol").asText();
                Object[] meta = INDEX_META.get(symbol);
                if (meta == null) continue;

                GlobalIndexEntry entry = new GlobalIndexEntry(symbol, (String) meta[0], (String) meta[1], (Region) meta[2]);
                if (item.has("regularMarketPrice"))
                    entry.setLtp(item.get("regularMarketPrice").decimalValue());
                if (item.has("regularMarketChange"))
                    entry.setChange(item.get("regularMarketChange").decimalValue());
                if (item.has("regularMarketChangePercent"))
                    entry.setChangePercent(item.get("regularMarketChangePercent").decimalValue());
                entry.setLastUpdated(LocalDateTime.now());
                result.add(entry);
            }
            logger.info("Yahoo Finance: fetched {} global indices", result.size());
        } catch (Exception e) {
            logger.error("Failed to fetch global indices from Yahoo Finance: {}", e.getMessage());
        }
        return result;
    }
}
