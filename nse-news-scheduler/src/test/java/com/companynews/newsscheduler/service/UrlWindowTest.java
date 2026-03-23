package com.companynews.newsscheduler.service;

import com.companynews.newsscheduler.repository.CompanyNewsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UrlWindowTest {

    @Mock
    private CompanyNewsRepository companyNewsRepository;

    private UrlWindow urlWindow;

    @BeforeEach
    void setUp() {
        // Use windowSize=3 to make eviction tests easy.
        // PostConstruct is NOT called in unit test context — window starts empty.
        urlWindow = new UrlWindow(3, companyNewsRepository);
    }

    @Test
    void addIfAbsent_returns_true_for_new_url() {
        assertThat(urlWindow.addIfAbsent("https://example.com/article1")).isTrue();
    }

    @Test
    void addIfAbsent_returns_false_for_duplicate_url() {
        urlWindow.addIfAbsent("https://example.com/article1");
        assertThat(urlWindow.addIfAbsent("https://example.com/article1")).isFalse();
    }

    @Test
    void addIfAbsent_returns_false_for_null() {
        assertThat(urlWindow.addIfAbsent(null)).isFalse();
    }

    @Test
    void getWindowSize_reflects_unique_urls_added() {
        urlWindow.addIfAbsent("https://a.com/1");
        urlWindow.addIfAbsent("https://a.com/2");
        urlWindow.addIfAbsent("https://a.com/1"); // duplicate — not counted
        assertThat(urlWindow.getWindowSize()).isEqualTo(2);
    }

    @Test
    void oldest_url_is_evicted_when_window_is_full() {
        urlWindow.addIfAbsent("https://a.com/1");
        urlWindow.addIfAbsent("https://a.com/2");
        urlWindow.addIfAbsent("https://a.com/3");
        assertThat(urlWindow.getWindowSize()).isEqualTo(3);

        // Adding 4th evicts the first (oldest)
        urlWindow.addIfAbsent("https://a.com/4");
        assertThat(urlWindow.getWindowSize()).isEqualTo(3);
        assertThat(urlWindow.addIfAbsent("https://a.com/1")).isTrue();  // evicted — treated as new
        assertThat(urlWindow.addIfAbsent("https://a.com/4")).isFalse(); // still in window
    }

    @Test
    void window_never_exceeds_configured_capacity() {
        for (int i = 0; i < 10; i++) {
            urlWindow.addIfAbsent("https://a.com/" + i);
        }
        assertThat(urlWindow.getWindowSize()).isEqualTo(3);
    }

    @Test
    void seedFromDb_populates_window_from_repository() {
        // Arrange: set up mock data that repository.findAll() returns
        var item1 = new com.companynews.newsscheduler.dto.NewsItem();
        item1.setLink("https://seeded.com/1");
        var item2 = new com.companynews.newsscheduler.dto.NewsItem();
        item2.setLink("https://seeded.com/2");

        var cn = new com.companynews.newsscheduler.model.CompanyNews();
        cn.setNews(List.of(item1, item2));

        when(companyNewsRepository.findAll()).thenReturn(List.of(cn));

        // Act: create a new window and call seedFromDb manually
        UrlWindow seeded = new UrlWindow(10, companyNewsRepository);
        seeded.seedFromDb();

        // Assert: URLs from DB are now in the window (addIfAbsent returns false = already seen)
        assertThat(seeded.addIfAbsent("https://seeded.com/1")).isFalse();
        assertThat(seeded.addIfAbsent("https://seeded.com/2")).isFalse();
        assertThat(seeded.addIfAbsent("https://brand-new.com/3")).isTrue();
    }
}
