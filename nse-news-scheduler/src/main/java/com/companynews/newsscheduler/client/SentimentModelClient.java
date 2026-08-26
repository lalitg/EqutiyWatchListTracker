package com.companynews.newsscheduler.client;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.LongBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Runs the FinBERT sentiment model inside this JVM using ONNX Runtime on CPU.
 *
 * <h2>Why in-process rather than a Python sidecar</h2>
 * An always-on Python service was previously a repeated contributor to EC2 OOM kills, and a
 * short-lived subprocess would pay interpreter and model-load cost on every 15-minute cycle.
 * Running the quantized model directly in the scheduler JVM avoids both: one load, then
 * millisecond-scale inference for the life of the process.
 *
 * <h2>Memory characteristics — important</h2>
 * ONNX Runtime allocates model weights and its working arena in <b>native</b> memory, which is
 * <b>outside the JVM heap and therefore not bounded by {@code -Xmx}</b>. Size the instance on
 * total RSS, not heap. Two consequences drive the configuration below:
 * <ul>
 *   <li>The CPU arena allocator is <b>disabled</b> by default. It normally pre-allocates
 *       generously and never returns memory to the OS — the right trade for a high-throughput
 *       server, pure waste for a job that scores a few hundred headlines then idles.</li>
 *   <li>The session is created <b>exactly once and never reloaded</b>. ONNX Runtime has a
 *       documented memory-growth defect on repeated model loading, so the intuitive
 *       "unload between cycles to save memory" optimisation actively backfires: native RSS
 *       climbs while the JVM heap still looks healthy.</li>
 * </ul>
 *
 * <h2>Tokenizer parity</h2>
 * Tokenization uses DJL's binding to the same Rust tokenizer library HuggingFace uses, loaded
 * from the {@code tokenizer.json} produced by the export. This matters more than it looks: a
 * hand-rolled WordPiece implementation loads fine, runs fine, and returns quietly wrong scores.
 *
 * <h2>Failure policy</h2>
 * Every failure path degrades to "no sentiment" rather than propagating. Sentiment is an
 * auxiliary signal; a missing model file or a malformed headline must never prevent news from
 * being fetched, deduplicated, or served.
 */
@Component
public class SentimentModelClient {

    private static final Logger log = LogManager.getLogger(SentimentModelClient.class);

    /** Canonical output order used by every consumer: index 0 positive, 1 neutral, 2 negative. */
    public static final int POSITIVE = 0;
    public static final int NEUTRAL  = 1;
    public static final int NEGATIVE = 2;

    private static final String MODEL_FILE     = "sentiment-model.onnx";
    private static final String TOKENIZER_FILE = "tokenizer.json";
    private static final String CONFIG_FILE    = "config.json";

    private final boolean enabled;
    private final String  modelDir;
    private final int     maxLength;
    private final int     intraOpThreads;
    private final int     interOpThreads;
    private final boolean arenaEnabled;
    private final boolean memoryPatternEnabled;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile boolean initialised = false;
    private volatile boolean unavailable = false;

    private OrtEnvironment       env;
    private OrtSession           session;
    private HuggingFaceTokenizer tokenizer;
    private Set<String>          modelInputNames;

    /**
     * Maps the model's own output index to our canonical index.
     *
     * <p>Read from {@code config.json} at load time and never hardcoded. Different FinBERT
     * variants order their labels differently, and assuming the wrong order inverts every
     * score silently — no exception, no log line, just backwards sentiment across the product.
     */
    private int[] canonicalIndexByModelIndex;

    public SentimentModelClient(
            @Value("${sentiment.enabled:true}") boolean enabled,
            @Value("${sentiment.model.path:models}") String modelDir,
            @Value("${sentiment.model.max-length:64}") int maxLength,
            @Value("${sentiment.onnx.intra-op-threads:1}") int intraOpThreads,
            @Value("${sentiment.onnx.inter-op-threads:1}") int interOpThreads,
            @Value("${sentiment.onnx.cpu-arena-enabled:false}") boolean arenaEnabled,
            @Value("${sentiment.onnx.memory-pattern-enabled:false}") boolean memoryPatternEnabled) {
        this.enabled              = enabled;
        this.modelDir             = modelDir;
        this.maxLength            = maxLength;
        this.intraOpThreads       = intraOpThreads;
        this.interOpThreads       = interOpThreads;
        this.arenaEnabled         = arenaEnabled;
        this.memoryPatternEnabled = memoryPatternEnabled;
    }

    /**
     * Returns whether scoring can currently be performed.
     *
     * <p>Triggers a one-time lazy load on first call. Loading lazily rather than at startup
     * means an instance where sentiment is disabled — or simply never exercised — pays no
     * memory cost at all.
     *
     * @return {@code true} if the model is loaded and ready
     */
    public boolean isAvailable() {
        if (!enabled || unavailable) return false;
        if (!initialised) initialise();
        return !unavailable;
    }

    /**
     * Scores a single headline.
     *
     * @param text the headline to classify; blank or null yields {@code null}
     * @return probabilities in canonical order — {@code [positive, neutral, negative]},
     *         summing to 1.0 — or {@code null} if scoring is unavailable or failed
     */
    public double[] predict(String text) {
        if (text == null || text.isBlank()) return null;
        if (!isAvailable()) return null;

        try {
            Encoding encoding = tokenizer.encode(text);
            long[] ids  = encoding.getIds();
            long[] mask = encoding.getAttentionMask();
            long[] type = encoding.getTypeIds();

            // Defensive: the tokenizer is configured to truncate, but a mismatched
            // tokenizer.json could still hand back a longer sequence than the model accepts.
            if (ids.length > maxLength) {
                ids  = Arrays.copyOf(ids, maxLength);
                mask = Arrays.copyOf(mask, maxLength);
                type = Arrays.copyOf(type, maxLength);
            }

            long[] shape = { 1, ids.length };
            Map<String, OnnxTensor> inputs = new HashMap<>();

            try {
                inputs.put("input_ids", OnnxTensor.createTensor(env, LongBuffer.wrap(ids), shape));
                if (modelInputNames.contains("attention_mask")) {
                    inputs.put("attention_mask",
                            OnnxTensor.createTensor(env, LongBuffer.wrap(mask), shape));
                }
                // BERT-family models take token_type_ids; some exports omit it. Supplying an
                // input the graph does not declare is an error, so ask the session first.
                if (modelInputNames.contains("token_type_ids")) {
                    inputs.put("token_type_ids",
                            OnnxTensor.createTensor(env, LongBuffer.wrap(type), shape));
                }

                try (OrtSession.Result result = session.run(inputs)) {
                    float[][] logits = (float[][]) result.get(0).getValue();
                    return softmaxToCanonical(logits[0]);
                }
            } finally {
                for (OnnxTensor tensor : inputs.values()) {
                    tensor.close();
                }
            }

        } catch (Exception e) {
            log.error("Sentiment inference failed for headline: {}", truncate(text), e);
            return null;
        }
    }

    /**
     * Converts raw model logits to probabilities and reorders them into canonical positions.
     *
     * <p>HuggingFace sequence-classification models emit unnormalised logits, so softmax is
     * applied here rather than in the graph. The maximum is subtracted first for numerical
     * stability — without it, large logits overflow {@link Math#exp}.
     */
    private double[] softmaxToCanonical(float[] logits) {
        double max = Double.NEGATIVE_INFINITY;
        for (float v : logits) max = Math.max(max, v);

        double sum = 0.0;
        double[] exp = new double[logits.length];
        for (int i = 0; i < logits.length; i++) {
            exp[i] = Math.exp(logits[i] - max);
            sum += exp[i];
        }

        double[] canonical = new double[3];
        for (int modelIdx = 0; modelIdx < logits.length; modelIdx++) {
            canonical[canonicalIndexByModelIndex[modelIdx]] = exp[modelIdx] / sum;
        }
        return canonical;
    }

    /** Loads the model, tokenizer and label mapping exactly once. */
    private synchronized void initialise() {
        if (initialised) return;
        initialised = true;

        Path dir       = Paths.get(modelDir);
        Path modelPath = dir.resolve(MODEL_FILE);
        Path tokPath   = dir.resolve(TOKENIZER_FILE);
        Path cfgPath   = dir.resolve(CONFIG_FILE);

        for (Path required : new Path[]{ modelPath, tokPath, cfgPath }) {
            if (!Files.isReadable(required)) {
                log.warn("Sentiment model artefact missing or unreadable: {} — sentiment scoring "
                       + "is DISABLED. News fetching and serving are unaffected.",
                         required.toAbsolutePath());
                unavailable = true;
                return;
            }
        }

        try {
            canonicalIndexByModelIndex = readLabelMapping(cfgPath);

            tokenizer = HuggingFaceTokenizer.builder()
                    .optTokenizerPath(tokPath)
                    .optMaxLength(maxLength)
                    .optTruncation(true)
                    .build();

            env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
            opts.setIntraOpNumThreads(intraOpThreads);
            opts.setInterOpNumThreads(interOpThreads);
            opts.setCPUArenaAllocator(arenaEnabled);
            opts.setMemoryPatternOptimization(memoryPatternEnabled);
            opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);

            session = env.createSession(modelPath.toString(), opts);
            modelInputNames = session.getInputNames();

            log.info("Sentiment model loaded from {} ({} MB, inputs={}, maxLength={}, "
                   + "threads={}/{}, arena={})",
                     modelPath.toAbsolutePath(),
                     new File(modelPath.toString()).length() / (1024 * 1024),
                     modelInputNames, maxLength, intraOpThreads, interOpThreads, arenaEnabled);

        } catch (Exception e) {
            log.error("Failed to initialise sentiment model — scoring DISABLED. "
                    + "News fetching and serving are unaffected.", e);
            unavailable = true;
            closeQuietly();
        }
    }

    /**
     * Builds the model-index to canonical-index mapping from {@code config.json}.
     *
     * <p>Fails loudly on an unrecognised label rather than guessing. A silent mis-mapping here
     * would invert sentiment across the entire product with no error anywhere, which is far
     * worse than refusing to start scoring.
     */
    private int[] readLabelMapping(Path configPath) throws Exception {
        JsonNode id2label = objectMapper.readTree(configPath.toFile()).get("id2label");
        if (id2label == null || !id2label.isObject()) {
            throw new IllegalStateException("config.json has no usable id2label object");
        }

        int[] mapping = new int[id2label.size()];
        boolean[] seen = new boolean[3];

        var fields = id2label.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            int modelIdx = Integer.parseInt(entry.getKey());
            String label = entry.getValue().asText().trim().toLowerCase();

            int canonical;
            if (label.startsWith("pos")) {
                canonical = POSITIVE;
            } else if (label.startsWith("neg")) {
                canonical = NEGATIVE;
            } else if (label.startsWith("neu")) {
                canonical = NEUTRAL;
            } else {
                throw new IllegalStateException(
                        "Cannot interpret label " + modelIdx + " -> " + entry.getValue().asText()
                      + ". Inspect the model card and map it explicitly before enabling scoring.");
            }

            mapping[modelIdx] = canonical;
            seen[canonical] = true;
        }

        if (!seen[POSITIVE] || !seen[NEUTRAL] || !seen[NEGATIVE]) {
            throw new IllegalStateException("id2label does not cover all three classes: " + id2label);
        }

        log.info("Sentiment label mapping resolved from config.json: {}", id2label);
        return mapping;
    }

    private static String truncate(String s) {
        return s.length() <= 80 ? s : s.substring(0, 80) + "...";
    }

    /** Releases native ONNX resources on shutdown. */
    @PreDestroy
    public void closeQuietly() {
        try {
            if (session != null) {
                session.close();
                session = null;
            }
        } catch (Exception e) {
            log.debug("Error closing ONNX session", e);
        }
        try {
            if (tokenizer != null) {
                tokenizer.close();
                tokenizer = null;
            }
        } catch (Exception e) {
            log.debug("Error closing tokenizer", e);
        }
        // OrtEnvironment is a JVM-wide singleton — deliberately not closed here.
    }
}
