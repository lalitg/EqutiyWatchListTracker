package com.companynews.newsscheduler.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RssFetcherTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<InputStream> httpResponse;

    private RssFetcher rssFetcher;

    private static final String TEST_RSS_BASE = "https://test.rss.local/rss?q=";

    private static final String VALID_RSS_XML = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0">
          <channel>
            <item>
              <title>Infosys Q3 results beat expectations</title>
              <link>https://news.example.com/infosys-q3</link>
              <pubDate>Thu, 12 Mar 2026 10:00:00 GMT</pubDate>
            </item>
            <item>
              <title>Infosys wins major cloud contract</title>
              <link>https://news.example.com/infosys-cloud</link>
              <pubDate>Wed, 11 Mar 2026 08:00:00 GMT</pubDate>
            </item>
          </channel>
        </rss>
        """;

    @BeforeEach
    void setUp() {
        rssFetcher = new RssFetcher(httpClient, 10, TEST_RSS_BASE, new SimpleMeterRegistry());
        // Set newsLimit via ReflectionTestUtils since it's @Value field injection
        ReflectionTestUtils.setField(rssFetcher, "newsLimit", 5);
    }

    // ── Happy path ─────────────────────────────────────────────────────────

    @Test
    void fetch_returns_parsed_items_on_200_response() throws Exception {
        InputStream xml = new ByteArrayInputStream(VALID_RSS_XML.getBytes(StandardCharsets.UTF_8));
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(xml);
        doReturn(httpResponse).when(httpClient).send(any(), any());

        var result = rssFetcher.fetch("INFY");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getLink()).isEqualTo("https://news.example.com/infosys-q3");
        assertThat(result.get(0).getSummary()).contains("Infosys Q3 results");
        assertThat(result.get(0).getDate()).isEqualTo("Thu, 12 Mar 2026 10:00:00 GMT");
    }

    @Test
    void fetch_respects_news_limit() throws Exception {
        // newsLimit = 1 → only first item returned
        ReflectionTestUtils.setField(rssFetcher, "newsLimit", 1);

        InputStream xml = new ByteArrayInputStream(VALID_RSS_XML.getBytes(StandardCharsets.UTF_8));
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(xml);
        doReturn(httpResponse).when(httpClient).send(any(), any());

        var result = rssFetcher.fetch("INFY");

        assertThat(result).hasSize(1);
    }

    @Test
    void fetch_skips_items_with_missing_link() throws Exception {
        String xmlWithMissingLink = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
              <channel>
                <item>
                  <title>No link article</title>
                  <pubDate>Thu, 12 Mar 2026 10:00:00 GMT</pubDate>
                </item>
                <item>
                  <title>Valid article</title>
                  <link>https://valid.com/article</link>
                  <pubDate>Thu, 12 Mar 2026 10:00:00 GMT</pubDate>
                </item>
              </channel>
            </rss>
            """;
        InputStream xml = new ByteArrayInputStream(xmlWithMissingLink.getBytes(StandardCharsets.UTF_8));
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(xml);
        doReturn(httpResponse).when(httpClient).send(any(), any());

        var result = rssFetcher.fetch("test");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLink()).isEqualTo("https://valid.com/article");
    }

    // ── Non-200 response ───────────────────────────────────────────────────

    @Test
    void fetch_throws_on_non_200_response() throws Exception {
        when(httpResponse.statusCode()).thenReturn(429);
        doReturn(httpResponse).when(httpClient).send(any(), any());

        // Resilience4j AOP not active in unit test — exception propagates directly
        assertThatThrownBy(() -> rssFetcher.fetch("INFY"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("429");
    }

    // ── IOException wrapping ───────────────────────────────────────────────

    @Test
    void fetch_wraps_IOException_as_RuntimeException() throws Exception {
        when(httpClient.send(any(), any())).thenThrow(new IOException("Network unreachable"));

        assertThatThrownBy(() -> rssFetcher.fetch("INFY"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("RSS fetch failed");
    }

    // ── Malformed XML ──────────────────────────────────────────────────────

    @Test
    void fetch_returns_empty_list_on_malformed_xml() throws Exception {
        InputStream badXml = new ByteArrayInputStream("NOT XML AT ALL".getBytes(StandardCharsets.UTF_8));
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(badXml);
        doReturn(httpResponse).when(httpClient).send(any(), any());

        // XML parse errors are caught internally — returns empty list (not thrown)
        assertThat(rssFetcher.fetch("INFY")).isEqualTo(List.of());
    }

    // ── Empty feed ─────────────────────────────────────────────────────────

    @Test
    void fetch_returns_empty_list_for_feed_with_no_items() throws Exception {
        String emptyFeed = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0"><channel></channel></rss>
            """;
        InputStream xml = new ByteArrayInputStream(emptyFeed.getBytes(StandardCharsets.UTF_8));
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(xml);
        doReturn(httpResponse).when(httpClient).send(any(), any());

        assertThat(rssFetcher.fetch("INFY")).isEmpty();
    }
}
