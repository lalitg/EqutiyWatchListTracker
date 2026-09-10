package com.companynews.newsscheduler.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the lazy initialisation of {@link SentimentModelClient}.
 *
 * <h2>The bug these exist for</h2>
 * {@code initialise()} used to set its {@code initialised} flag on entry rather than on
 * completion. The method is {@code synchronized}, but {@code isAvailable()} reads the flag
 * without taking the lock, so a second thread arriving during the several seconds it takes to
 * build the tokenizer and ONNX session would see the flag already set, skip initialisation, and
 * call {@code predict} against a {@code null} tokenizer.
 *
 * <p>In production the news scheduler runs several RSS workers in parallel and they all reach
 * scoring at once on the first fetch after startup, so this fired on essentially every run —
 * throwing a burst of {@code NullPointerException}s and leaving those headlines <em>permanently</em>
 * unscored, because scoring happens once at save time and nothing revisits an article afterwards.
 *
 * <p>The concurrency test below reliably reproduced it before the fix: the load takes long enough
 * that eight threads released together will always overlap it.
 */
class SentimentModelClientConcurrencyTest {

    private static final String MODEL_DIR =
        System.getProperty("sentiment.model.path", "models");

    private static final int THREADS = 8;

    /** Skips the model-backed test when the 219 MB artefact is not present in this checkout. */
    @SuppressWarnings("unused")
    static boolean modelPresent() {
        Path dir = Paths.get(MODEL_DIR);
        return Files.isReadable(dir.resolve("sentiment-model.onnx"))
            && Files.isReadable(dir.resolve("tokenizer.json"))
            && Files.isReadable(dir.resolve("config.json"));
    }

    private static SentimentModelClient client(String modelDir) {
        return new SentimentModelClient(true, modelDir, 64, 1, 1, false, false);
    }

    @Test
    @EnabledIf("modelPresent")
    @DisplayName("concurrent first calls all score, none observes a half-built model")
    void concurrentColdStartIsSafe() throws Exception {
        SentimentModelClient client = client(MODEL_DIR);

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(THREADS);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicInteger scored = new AtomicInteger();

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            Thread thread = new Thread(() -> {
                try {
                    start.await();                       // release all at once, into a cold client
                    double[] probabilities = client.predict("Profit beats estimates on strong sales");
                    if (probabilities != null) scored.incrementAndGet();
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                } finally {
                    done.countDown();
                }
            }, "score-" + i);
            thread.start();
            threads.add(thread);
        }

        start.countDown();
        assertTrue(done.await(120, TimeUnit.SECONDS), "threads did not finish in time");
        for (Thread thread : threads) thread.join();

        assertNull(failure.get(), "a thread saw a half-initialised model: " + failure.get());
        // Every thread must get a real answer. Before the fix the losers of the race threw an
        // NPE, which SentimentScorer swallowed into a null score — a silently unscored headline.
        assertEquals(THREADS, scored.get(), "some threads got no score from a cold client");
    }

    @Test
    @DisplayName("a missing artefact disables scoring without throwing")
    void missingArtefactDisablesScoring() {
        SentimentModelClient client = client("no-such-directory-for-tests");

        assertFalse(client.isAvailable());
        assertNull(client.predict("Profit beats estimates"));
    }

    @Test
    @DisplayName("a failed load is not retried on every subsequent call")
    void failedLoadIsNotRetried() {
        // The flag is set in a finally block, so a failure marks the client permanently
        // unavailable. Without that, every headline in every fetch cycle would re-attempt to
        // open a 219 MB file that is not there.
        SentimentModelClient client = client("no-such-directory-for-tests");

        for (int i = 0; i < 5; i++) {
            assertFalse(client.isAvailable());
        }
        assertNull(client.predict("Profit beats estimates"));
    }

    @Test
    @DisplayName("a disabled client never touches the filesystem")
    void disabledClientStaysDisabled() {
        SentimentModelClient client =
            new SentimentModelClient(false, MODEL_DIR, 64, 1, 1, false, false);

        assertFalse(client.isAvailable());
        assertNull(client.predict("Profit beats estimates"));
    }
}
