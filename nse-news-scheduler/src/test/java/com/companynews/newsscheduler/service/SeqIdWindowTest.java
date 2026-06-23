package com.companynews.newsscheduler.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeqIdWindowTest {

    // Use a small window size so eviction tests are easy to write
    private SeqIdWindow window;

    @BeforeEach
    void setUp() {
        window = new SeqIdWindow(3); // capacity of 3 for test clarity
    }

    @Test
    void contains_returns_false_for_unseen_seqId() {
        assertThat(window.contains(100L)).isFalse();
    }

    @Test
    void add_then_contains_returns_true() {
        window.add(100L);
        assertThat(window.contains(100L)).isTrue();
    }

    @Test
    void getWindowSize_reflects_actual_count() {
        assertThat(window.getWindowSize()).isEqualTo(0);
        window.add(100L);
        window.add(101L);
        assertThat(window.getWindowSize()).isEqualTo(2);
    }

    @Test
    void oldest_entry_evicted_when_window_is_full() {
        // Fill to capacity
        window.add(100L);
        window.add(101L);
        window.add(102L);
        assertThat(window.getWindowSize()).isEqualTo(3);

        // Adding 103 should evict 100 (oldest/smallest)
        window.add(103L);
        assertThat(window.getWindowSize()).isEqualTo(3);
        assertThat(window.contains(100L)).isFalse(); // evicted
        assertThat(window.contains(103L)).isTrue();  // newest retained
    }

    @Test
    void window_never_exceeds_configured_capacity() {
        for (long i = 0; i < 10; i++) {
            window.add(i);
        }
        assertThat(window.getWindowSize()).isEqualTo(3);
    }

    @Test
    void duplicate_add_does_not_increase_size() {
        window.add(100L);
        window.add(100L); // same value again
        assertThat(window.getWindowSize()).isEqualTo(1);
    }

    @Test
    void getWindow_returns_defensive_copy() {
        window.add(100L);
        var copy = window.getWindow();
        copy.add(999L); // mutate the copy

        // Internal window should NOT be affected
        assertThat(window.contains(999L)).isFalse();
        assertThat(window.getWindowSize()).isEqualTo(1);
    }
}
