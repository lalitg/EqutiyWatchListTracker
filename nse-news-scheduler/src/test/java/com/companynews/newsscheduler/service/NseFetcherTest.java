package com.companynews.newsscheduler.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NseFetcherTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private NseFetcher nseFetcher;

    private static final String TEST_NSE_URL = "https://test.nse.local/api/announcements";

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        // Construct directly — bypasses Spring context and @Value injection
        // SimpleMeterRegistry is an in-memory no-op registry suitable for unit tests
        nseFetcher = new NseFetcher(mapper, httpClient, 10, TEST_NSE_URL, new SimpleMeterRegistry());
    }

    // ── Happy path ─────────────────────────────────────────────────────────

    @Test
    void fetch_returns_announcements_on_200_response() throws Exception {
        String json = """
            [
              {
                "symbol": "INFY",
                "an_dt": "12-Mar-2026 10:00:00",
                "attchmntText": "Q3 results",
                "attchmntFile": "https://nse.com/file.pdf",
                "seq_id": "12345"
              }
            ]
            """;

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(json);
        doReturn(httpResponse).when(httpClient).send(any(), any());

        var result = nseFetcher.fetch();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNewsItem().getSymbol()).isEqualTo("INFY");
        assertThat(result.get(0).getSeqId()).isEqualTo(12345L);
        assertThat(result.get(0).getNewsItem().getSummary()).contains("Q3 results");
        assertThat(result.get(0).getNewsItem().getSummary()).endsWith("- NSE");
    }

    @Test
    void fetch_skips_entries_missing_symbol_or_seqId() throws Exception {
        String json = """
            [
              { "symbol": null, "seq_id": "100", "attchmntText": "no symbol", "attchmntFile": "x" },
              { "symbol": "TCS", "seq_id": null, "attchmntText": "no seqid", "attchmntFile": "x" },
              { "symbol": "TCS", "seq_id": "200", "attchmntText": "valid", "attchmntFile": "x", "an_dt": "12-Mar-2026 10:00:00" }
            ]
            """;
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(json);
        doReturn(httpResponse).when(httpClient).send(any(), any());

        var result = nseFetcher.fetch();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNewsItem().getSymbol()).isEqualTo("TCS");
    }

    // ── Non-200 response ───────────────────────────────────────────────────

    @Test
    void fetch_throws_on_non_200_response() throws Exception {
        when(httpResponse.statusCode()).thenReturn(503);
        doReturn(httpResponse).when(httpClient).send(any(), any());

        // In unit test context resilience4j AOP is NOT active — exception propagates directly
        assertThatThrownBy(() -> nseFetcher.fetch())
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("503");
    }

    // ── IOException wrapping ───────────────────────────────────────────────

    @Test
    void fetch_wraps_IOException_as_RuntimeException() throws Exception {
        doReturn(null).when(httpClient).send(any(), any());
        when(httpClient.send(any(), any())).thenThrow(new IOException("Connection refused"));

        assertThatThrownBy(() -> nseFetcher.fetch())
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("NSE fetch failed");
    }

    // ── Empty JSON array ───────────────────────────────────────────────────

    @Test
    void fetch_returns_empty_list_on_empty_json_array() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("[]");
        doReturn(httpResponse).when(httpClient).send(any(), any());

        assertThat(nseFetcher.fetch()).isEmpty();
    }

    // ── Malformed JSON ─────────────────────────────────────────────────────

    @Test
    void fetch_returns_empty_list_on_malformed_json() throws Exception {
        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("NOT_VALID_JSON{{{");
        doReturn(httpResponse).when(httpClient).send(any(), any());

        // JSON parse errors are caught internally (non-retriable) — returns empty list
        assertThat(nseFetcher.fetch()).isEqualTo(List.of());
    }
}
