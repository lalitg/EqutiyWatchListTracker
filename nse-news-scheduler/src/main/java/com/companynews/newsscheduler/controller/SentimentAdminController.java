package com.companynews.newsscheduler.controller;

import com.companynews.newsscheduler.service.SentimentBackfillService;
import com.companynews.newsscheduler.service.SentimentScorer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Internal operations endpoints for the sentiment feature.
 *
 * <p>Lives under {@code /api/internal}, the same prefix as the existing watchlist event hook,
 * signalling that these are not public product APIs. They are intended for deployment and
 * diagnostics, invoked from the server or through an internal tool.
 *
 * <p>These endpoints are deliberately kept out of {@link NewsController} so the public news API
 * stays focused on serving news, and so restricting the internal prefix at the ingress layer
 * covers all of them at once.
 */
@RestController
@RequestMapping("/api/internal/sentiment")
public class SentimentAdminController {

    private static final Logger log = LogManager.getLogger(SentimentAdminController.class);

    private final SentimentBackfillService backfillService;
    private final SentimentScorer scorer;

    public SentimentAdminController(SentimentBackfillService backfillService,
                                    SentimentScorer scorer) {
        this.backfillService = backfillService;
        this.scorer          = scorer;
    }

    /**
     * Reports whether the model loaded and whether a backfill is in progress.
     *
     * <p>The first call also triggers the lazy model load, so this doubles as a way to warm the
     * model and confirm the artefacts are readable without waiting for a news cycle.
     *
     * <p>{@code GET /api/internal/sentiment/status}
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("modelAvailable",  scorer.isAvailable());
        body.put("backfillRunning", backfillService.isRunning());
        return ResponseEntity.ok(body);
    }

    /**
     * Scores headlines stored before this feature existed.
     *
     * <p>Needed once after deployment: scoring happens at save time, so articles already in the
     * database carry no score and their companies would otherwise show NO_DATA until fresh news
     * happens to arrive — potentially days for a quiet company.
     *
     * <p>Returns immediately and runs on a background thread; a full pass takes minutes, well
     * beyond any sensible HTTP timeout. Poll {@code /status} to follow progress, and watch the
     * application log for the completion summary. The job is idempotent, so re-running it after
     * an interruption is safe.
     *
     * <p>{@code POST /api/internal/sentiment/backfill}
     */
    @PostMapping("/backfill")
    public ResponseEntity<Map<String, Object>> backfill() {
        boolean started = backfillService.startAsync();
        log.info("Sentiment backfill requested — started={}", started);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("started", started);
        body.put("message", started
                ? "Backfill started in background. Poll /api/internal/sentiment/status."
                : "Not started — either already running or the model is unavailable.");
        return ResponseEntity.ok(body);
    }
}
