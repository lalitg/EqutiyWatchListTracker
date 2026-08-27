package com.equity.sebi.service;

import com.equity.sebi.model.NseMatchResult;
import com.equity.sebi.model.RssMatchResult;
import com.equity.sebi.model.SebiRssPocResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SebiRssService {

    private static final Logger log = LogManager.getLogger(SebiRssService.class);

    private static final List<Pattern> EXTRACT_PATTERNS = List.of(
        Pattern.compile(
            "in respect of ([^(\\n]{4,80}?)(?=\\s*\\(PAN|\\s+in the matter|\\s+in the\\b)",
            Pattern.CASE_INSENSITIVE),
        Pattern.compile(
            "issued to ([^(\\[\\n]{4,80}?)(?=\\s*\\(PAN|\\s*\\[)",
            Pattern.CASE_INSENSITIVE),
        Pattern.compile(
            "against ([^(\\n]{4,80}?)(?=\\s+in the matter|\\s*\\(PAN)",
            Pattern.CASE_INSENSITIVE),
        Pattern.compile(
            "\\bOrder in the matter of ([A-Za-z][^.\\n]{4,80}?)(?=\\.|$)",
            Pattern.CASE_INSENSITIVE),
        Pattern.compile(
            "\\bin the matter of ([A-Z][A-Za-z0-9 &.,'-]{4,80}?)(?=\\.|$)",
            Pattern.CASE_INSENSITIVE)
    );

    private static final List<String[]> EVENT_TYPE_MAP = List.of(
        new String[]{"recovery-proceedings", "RECOVERY_NOTICE"},
        new String[]{"enforcement/orders",   "ADJUDICATION_ORDER"},
        new String[]{"enforcement/directions","DIRECTION"},
        new String[]{"legal/circulars",      "CIRCULAR"},
        new String[]{"legal/regulations",    "REGULATION"},
        new String[]{"filings/buybacks",     "BUYBACK"},
        new String[]{"filings/",             "FILING"}
    );

    private static final long CACHE_TTL_MS = 15 * 60 * 1000L;

    private final AtomicReference<SebiRssPocResponse> cachedResponse = new AtomicReference<>();
    private volatile Instant cacheExpiry = Instant.EPOCH;

    @Value("${sebi.rss.url}")
    private String rssUrl;

    private final NseCompanyStore nseStore;

    public SebiRssService(NseCompanyStore nseStore) {
        this.nseStore = nseStore;
    }

    public SebiRssPocResponse fetchAndMatch() {
        if (Instant.now().isBefore(cacheExpiry) && cachedResponse.get() != null) {
            log.info("RSS cache hit — returning cached response (expires {})", cacheExpiry);
            return cachedResponse.get();
        }

        String fetchedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        List<RssItem> items = fetchRssItems();
        log.info("Fetched {} RSS items from SEBI", items.size());

        List<RssMatchResult> matches = new ArrayList<>();
        for (RssItem item : items) {
            String eventType  = resolveEventType(item.link());
            Set<String> cands = extractCandidates(item.title(), eventType);
            tryMatch(item, eventType, cands).ifPresent(matches::add);
        }

        log.info("Matched {}/{} RSS items to NSE companies", matches.size(), items.size());
        SebiRssPocResponse response = new SebiRssPocResponse(fetchedAt, items.size(), matches.size(), matches);

        if (!items.isEmpty()) {
            cachedResponse.set(response);
            cacheExpiry = Instant.now().plusMillis(CACHE_TTL_MS);
            log.info("RSS response cached for 15 minutes");
        }
        return response;
    }

    /**
     * Returns only the RSS matches for a specific NSE symbol.
     * Triggers a full fetch+match (or uses cache), then filters.
     */
    public List<RssMatchResult> getMatchesForSymbol(String symbol) {
        SebiRssPocResponse full = fetchAndMatch();
        String upper = symbol.toUpperCase();
        return full.matches().stream()
                .filter(m -> upper.equals(m.nseSymbol()))
                .collect(Collectors.toList());
    }

    private Optional<RssMatchResult> tryMatch(RssItem item, String eventType, Set<String> candidates) {
        for (String candidate : candidates) {
            Optional<NseMatchResult> hit = nseStore.match(candidate);
            if (hit.isPresent()) {
                NseMatchResult m = hit.get();
                log.info("RSS MATCH [{}] '{}' → {} ({})", eventType, candidate, m.company().symbol(), m.tier());
                return Optional.of(new RssMatchResult(
                        m.company().symbol(),
                        m.company().name(),
                        candidate.trim(),
                        m.tier(),
                        eventType,
                        item.pubDate(),
                        item.link(),
                        item.title()
                ));
            }
        }
        return Optional.empty();
    }

    private static final List<String> NOISE_SUBSTRINGS = List.of(
        "illiquid stock option",
        "stock options on bse",
        "stock options at bse"
    );

    private Set<String> extractCandidates(String title, String eventType) {
        Set<String> candidates = new LinkedHashSet<>();
        for (Pattern p : EXTRACT_PATTERNS) {
            Matcher m = p.matcher(title);
            while (m.find()) {
                String candidate = m.group(1).trim()
                        .replaceAll("\\.$", "")
                        .replaceAll(",\\s*$", "")
                        .replaceAll("(?i)^against\\s+", "");
                candidate = candidate.trim();
                if (candidate.isBlank() || candidate.length() < 4) continue;
                if (isNoise(candidate)) continue;
                candidates.add(candidate);
            }
        }
        return candidates;
    }

    private boolean isNoise(String candidate) {
        String lower = candidate.toLowerCase();
        return NOISE_SUBSTRINGS.stream().anyMatch(lower::contains);
    }

    private String resolveEventType(String url) {
        if (url == null) return "OTHER";
        for (String[] entry : EVENT_TYPE_MAP) {
            if (url.contains(entry[0])) return entry[1];
        }
        return "OTHER";
    }

    private List<RssItem> fetchRssItems() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "curl", "--silent", "--max-time", "20",
                    "--header", "User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    "--header", "Accept: application/rss+xml, application/xml, text/xml, */*",
                    "--header", "Referer: https://www.sebi.gov.in/",
                    rssUrl
            );
            pb.redirectErrorStream(false);
            Process proc = pb.start();
            String xml = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exit = proc.waitFor();
            if (exit != 0 || xml.isBlank()) {
                log.error("curl exited with code {} for SEBI RSS", exit);
                return List.of();
            }
            log.info("curl fetched {} bytes from SEBI RSS", xml.length());
            return parseXml(xml);
        } catch (Exception e) {
            log.error("Failed to fetch SEBI RSS via curl: {}", e.getMessage());
            return List.of();
        }
    }

    private List<RssItem> parseXml(String xml) {
        List<RssItem> items = new ArrayList<>();
        if (xml == null || xml.isBlank()) return items;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);

            Document doc = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            NodeList itemNodes = doc.getElementsByTagName("item");
            for (int i = 0; i < itemNodes.getLength(); i++) {
                Element el = (Element) itemNodes.item(i);
                String title   = text(el, "title");
                String link    = text(el, "link");
                String pubDate = text(el, "pubDate");
                if (!title.isBlank()) items.add(new RssItem(title, link, pubDate));
            }
        } catch (Exception e) {
            log.error("XML parse error: {}", e.getMessage());
        }
        return items;
    }

    private String text(Element el, String tag) {
        NodeList nl = el.getElementsByTagName(tag);
        if (nl.getLength() == 0) return "";
        return nl.item(0).getTextContent().trim();
    }

    private record RssItem(String title, String link, String pubDate) {}
}
