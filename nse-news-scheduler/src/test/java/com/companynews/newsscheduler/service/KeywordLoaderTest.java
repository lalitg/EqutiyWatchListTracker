package com.companynews.newsscheduler.service;

import com.companynews.newsscheduler.repository.GlobalWatchlistRepository;
import com.companynews.newsscheduler.repository.SectorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeywordLoaderTest {

    @Mock
    private GlobalWatchlistRepository watchlistRepository;

    @Mock
    private SectorRepository sectorRepository;

    private KeywordLoader keywordLoader;

    @BeforeEach
    void setUp() {
        keywordLoader = new KeywordLoader(watchlistRepository, sectorRepository);
    }

    @Test
    void load_combines_symbols_and_sectors() {
        when(watchlistRepository.findAllSymbols()).thenReturn(List.of("INFY", "TCS"));
        when(sectorRepository.findAllSectorNames()).thenReturn(List.of("Banking", "Pharma"));

        List<String> result = keywordLoader.load();

        // Result must contain all four, order: symbols first then sectors
        assertThat(result).contains("INFY", "TCS", "Banking", "Pharma");
    }

    @Test
    void load_removes_duplicates_across_sources() {
        // "INFY" appears in both watchlist and sectors (unusual but possible)
        when(watchlistRepository.findAllSymbols()).thenReturn(List.of("INFY", "TCS"));
        when(sectorRepository.findAllSectorNames()).thenReturn(List.of("INFY", "Banking"));

        List<String> result = keywordLoader.load();

        // "INFY" should appear exactly once
        long infyCount = result.stream().filter("INFY"::equals).count();
        assertThat(infyCount).isEqualTo(1);
    }

    @Test
    void load_preserves_insertion_order_symbols_first() {
        when(watchlistRepository.findAllSymbols()).thenReturn(List.of("INFY", "TCS"));
        when(sectorRepository.findAllSectorNames()).thenReturn(List.of("Banking"));

        List<String> result = keywordLoader.load();

        // Symbols (from watchlist) must appear before sectors
        assertThat(result.indexOf("INFY")).isLessThan(result.indexOf("Banking"));
        assertThat(result.indexOf("TCS")).isLessThan(result.indexOf("Banking"));
    }

    @Test
    void load_handles_empty_watchlist_gracefully() {
        when(watchlistRepository.findAllSymbols()).thenReturn(List.of());
        when(sectorRepository.findAllSectorNames()).thenReturn(List.of("Banking"));

        List<String> result = keywordLoader.load();

        assertThat(result).containsExactly("Banking");
    }

    @Test
    void load_handles_empty_sectors_gracefully() {
        when(watchlistRepository.findAllSymbols()).thenReturn(List.of("INFY"));
        when(sectorRepository.findAllSectorNames()).thenReturn(List.of());

        List<String> result = keywordLoader.load();

        assertThat(result).contains("INFY");
    }

    @Test
    void load_returns_empty_list_when_all_sources_are_empty() {
        when(watchlistRepository.findAllSymbols()).thenReturn(List.of());
        when(sectorRepository.findAllSectorNames()).thenReturn(List.of());
        // keywords.txt is also absent in test classpath — gracefully skipped

        List<String> result = keywordLoader.load();

        assertThat(result).isEmpty();
    }

    @Test
    void load_continues_when_watchlist_db_throws() {
        when(watchlistRepository.findAllSymbols()).thenThrow(new RuntimeException("DB down"));
        when(sectorRepository.findAllSectorNames()).thenReturn(List.of("Banking"));

        // Should not propagate exception — just skip that source
        List<String> result = keywordLoader.load();

        assertThat(result).contains("Banking");
        assertThat(result).doesNotContain("INFY");
    }

    @Test
    void load_continues_when_sector_db_throws() {
        when(watchlistRepository.findAllSymbols()).thenReturn(List.of("INFY"));
        when(sectorRepository.findAllSectorNames()).thenThrow(new RuntimeException("DB down"));

        List<String> result = keywordLoader.load();

        assertThat(result).contains("INFY");
    }
}
