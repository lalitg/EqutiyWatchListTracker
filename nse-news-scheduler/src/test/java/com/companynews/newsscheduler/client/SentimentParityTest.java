package com.companynews.newsscheduler.client;

import com.companynews.newsscheduler.service.SentimentScorer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the Java inference path against the Python reference.
 *
 * <h2>Why this test exists</h2>
 * Tokenizer mismatch is the classic ONNX-in-Java failure: the model loads, inference runs,
 * nothing throws, and every score is quietly wrong. It cannot be caught by inspection, only by
 * comparing numbers. The expectations below were produced by {@code ml-poc/export_onnx.py}
 * running the same headlines through PyTorch and through the exported FP16 graph, where both
 * agreed to within 0.0007 probability.
 *
 * <h2>Skipped when the model is absent</h2>
 * The artefact is ~219 MB and deliberately not committed, so this test disables itself when it
 * cannot find one. That keeps a clean checkout building, at the cost of the test being silently
 * skipped — run it explicitly after producing or updating the artefact.
 *
 * <p>Point it at a non-default location with {@code -Dsentiment.model.path=/abs/path}.
 */
class SentimentParityTest {

    private static final String MODEL_DIR =
            System.getProperty("sentiment.model.path", "models");

    /** Tolerance on the -5..+5 scale. Python and Java run the identical graph, so any
     *  meaningful drift indicates a tokenization difference rather than numeric noise. */
    private static final double TOLERANCE = 0.05;

    /** Headlines and the scores Python produced for them. Keep in sync with export_onnx.py. */
    private static final Object[][] EXPECTED = {
        { "Reliance Industries Q1 profit beats estimates on strong Jio subscriber growth", +4.40 },
        { "Tata Motors shares plunge 8% after weak JLR sales data",                        -4.77 },
        { "HDFC Bank to hold board meeting on July 22 to consider results",                +0.01 },
        { "It was not a good quarter for Bajaj Finance as provisions rose sharply",        -0.45 },
        { "Maruti Suzuki beats profit estimates but guides margins lower for H2",          -4.37 },
    };

    static boolean modelPresent() {
        Path dir = Paths.get(MODEL_DIR);
        return Files.isReadable(dir.resolve("sentiment-model.onnx"))
            && Files.isReadable(dir.resolve("tokenizer.json"))
            && Files.isReadable(dir.resolve("config.json"));
    }

    private static SentimentModelClient newClient() {
        return new SentimentModelClient(
                true,       // enabled
                MODEL_DIR,
                64,         // max-length, matching the export
                1, 1,       // single-threaded, as in production
                false,      // cpu arena disabled
                false);     // memory pattern disabled
    }

    @Test
    @EnabledIf("modelPresent")
    @DisplayName("Java scores match the Python reference for every parity headline")
    void javaMatchesPython() {
        SentimentModelClient client = newClient();
        try {
            assertTrue(client.isAvailable(), "model failed to load from " + MODEL_DIR);

            SentimentScorer scorer = new SentimentScorer(client, 1.5, -1.5);
            StringBuilder report = new StringBuilder("\nJava vs Python parity:\n");
            double worstDelta = 0.0;

            for (Object[] row : EXPECTED) {
                String headline = (String) row[0];
                double expected = (Double) row[1];

                double[] probabilities = client.predict(headline);
                assertNotNull(probabilities, "no prediction for: " + headline);

                double actual = scorer.toScore(probabilities);
                double delta  = Math.abs(actual - expected);
                worstDelta = Math.max(worstDelta, delta);

                report.append(String.format("  python=%+.2f  java=%+.2f  delta=%.4f  %s%n",
                        expected, actual, delta, headline.substring(0, Math.min(44, headline.length()))));

                assertEquals(expected, actual, TOLERANCE,
                        "Java and Python disagree on: " + headline
                      + " — this almost always means the Java tokenizer is producing different "
                      + "token IDs, not that the model is wrong.");
            }

            report.append(String.format("  worst delta: %.4f (tolerance %.2f)%n", worstDelta, TOLERANCE));
            System.out.println(report);

        } finally {
            client.closeQuietly();
        }
    }

    @Test
    @EnabledIf("modelPresent")
    @DisplayName("probabilities are a valid distribution in canonical order")
    void probabilitiesAreWellFormed() {
        SentimentModelClient client = newClient();
        try {
            double[] p = client.predict("Reliance Q1 profit beats estimates");
            assertNotNull(p);
            assertEquals(3, p.length);
            assertEquals(1.0, p[0] + p[1] + p[2], 0.001, "probabilities must sum to 1");

            // Canonical order is positive, neutral, negative — verified here rather than
            // assumed, because a wrong label mapping inverts every score in the product
            // without raising anything.
            assertTrue(p[SentimentModelClient.POSITIVE] > p[SentimentModelClient.NEGATIVE],
                    "a clearly positive headline must score positive higher than negative — "
                  + "if this fails, id2label was mapped the wrong way round");
        } finally {
            client.closeQuietly();
        }
    }

    @Test
    @EnabledIf("modelPresent")
    @DisplayName("blank and null input return no prediction rather than throwing")
    void blankInputIsSafe() {
        SentimentModelClient client = newClient();
        try {
            org.junit.jupiter.api.Assertions.assertNull(client.predict(null));
            org.junit.jupiter.api.Assertions.assertNull(client.predict("   "));
        } finally {
            client.closeQuietly();
        }
    }
}
