package com.equity.config;

import com.equity.payment.enums.PlanTier;
import com.equity.payment.service.EntitlementService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The paywall, on the server side.
 *
 * <h2>Why here and not in each service</h2>
 * Every browser request enters through this JVM — the pages, the watchlist, and everything proxied
 * to the scheduler JVM (news, sentiment, events, AMFI, index constituents). The services behind it
 * are not reachable from the internet. One gate at the front door therefore covers all of them,
 * and the alternative — the same check copied into five services on five branches — would drift
 * the first time a tier changed.
 *
 * <h2>Two kinds of enforcement</h2>
 * <ul>
 *   <li><b>Refuse</b> — the whole endpoint belongs to a paid tier, so an unentitled caller gets a
 *       403 carrying the tier that would have worked.</li>
 *   <li><b>Trim</b> — one endpoint serves free and paid content in the same payload.
 *       {@code /api/news} carries the free headline list together with the sentiment scores and
 *       the Important News that are paid, so the response is filtered rather than refused. A 403
 *       there would take away something the reader is entitled to.</li>
 * </ul>
 *
 * <h2>What is deliberately not gated</h2>
 * {@code /api/events} and {@code /api/news/merged} serve the free market pages and the paid company
 * tabs through the same URL and the same parameters, with nothing in the request to tell the two
 * apart. {@code /api/v1/nse/holidays} feeds the market-status banner on every page, free or not.
 * Gating any of them by path would break the free product, so those three stay client-side locks
 * until the endpoints themselves are split. Each is market data that is public elsewhere, which is
 * why this is an acceptable place to stop.
 *
 * <h2>Writes are not here</h2>
 * Watchlist capacity and alert subscriptions are enforced in watchlist-service, where the rows
 * being counted live. This gate only answers "may you read this".
 */
public class EntitlementGate extends OncePerRequestFilter {

    private static final Logger logger = LogManager.getLogger(EntitlementGate.class);

    /** One rule: what has to be true of the path, the tier it needs, and the name the UI knows. */
    private record Rule(String feature, PlanTier required, java.util.function.Predicate<String> matches) {}

    private static final List<Rule> RULES = List.of(
            // ── Advanced ──────────────────────────────────────────────────────────────
            new Rule("sentiments", PlanTier.ADVANCED, p -> p.equals("/api/news/sentiment")),
            new Rule("extremes",   PlanTier.ADVANCED, p -> p.startsWith("/api/news/extremes")),
            // The bell and its panel. A free reader has no notifications - nothing creates one for
            // them - but the endpoint is theirs to query, and a plan is what makes it meaningful.
            new Rule("alerts",     PlanTier.ADVANCED, p -> p.startsWith("/api/v1/notifications")),

            // ── Basic ─────────────────────────────────────────────────────────────────
            new Rule("events",          PlanTier.BASIC, p -> p.startsWith("/api/macro-events")),
            new Rule("mutualFunds",     PlanTier.BASIC, p -> p.startsWith("/api/amfi")),
            new Rule("companyNewsTabs", PlanTier.BASIC, p -> p.startsWith("/api/sebi")),
            // The constituent lists behind an index or a sector card. The grids themselves
            // (/api/nse-code/indices and /api/nse-code/sectors) stay free, as does the lookup of
            // which indices a company belongs to, which the company page shows to everyone.
            new Rule("domesticDrilldown", PlanTier.BASIC, EntitlementGate::isConstituentList)
    );

    private static boolean isConstituentList(String path) {
        return (path.startsWith("/api/nse-code/indices/") || path.startsWith("/api/nse-code/sectors/"))
                && path.endsWith("/companies");
    }

    private final EntitlementService entitlementService;
    private final ObjectMapper objectMapper;

    public EntitlementGate(EntitlementService entitlementService, ObjectMapper objectMapper) {
        this.entitlementService = entitlementService;
        this.objectMapper       = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String path = request.getRequestURI();
        boolean trims = trimsNews(path);
        Rule rule = RULES.stream().filter(r -> r.matches().test(path)).findFirst().orElse(null);

        if (rule == null && !trims) {
            chain.doFilter(request, response);
            return;
        }

        // A CORS preflight carries no Authorization header by definition. Refusing it would break
        // the real request that follows, which this gate will see properly.
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        PlanTier tier = tierOf(request);

        if (rule != null && !tier.atLeast(rule.required())) {
            refuse(response, rule);
            return;
        }

        if (trims && !tier.atLeast(PlanTier.ADVANCED)) {
            trim(request, response, chain, tier);
            return;
        }

        chain.doFilter(request, response);
    }

    /** The two endpoints whose payload mixes free and paid content. */
    private boolean trimsNews(String path) {
        return path.equals("/api/news") || path.equals("/api/news/merged");
    }

    /**
     * The caller's tier.
     *
     * <p>No session means FREE, not a refusal: these paths are reachable without signing in, and a
     * visitor should meet the paywall rather than a login wall. {@code billing.enabled=false} makes
     * this ADVANCED for everyone, which is what makes the kill switch work here too.
     */
    private PlanTier tierOf(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long userId = (auth != null && auth.getPrincipal() instanceof Long id) ? id : null;
        return entitlementService.tierForUser(userId);
    }

    private void refuse(HttpServletResponse response, Rule rule) throws IOException {
        logger.debug("Refused {} - needs {}", rule.feature(), rule.required());
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(java.util.Map.of(
                "error", "This is part of a paid plan.",
                "code", "UPGRADE_REQUIRED",
                "requiredTier", rule.required().name(),
                "feature", rule.feature()
        )));
    }

    /**
     * Lets the request run, then removes the paid fields from the response.
     *
     * <p>The body is buffered because it has to be read before it can be rewritten; these two
     * endpoints return one page of news, so the buffer is kilobytes. Anything that is not JSON, or
     * that did not succeed, is passed through untouched — an error page is not ours to edit.
     */
    private void trim(HttpServletRequest request,
                      HttpServletResponse response,
                      FilterChain chain,
                      PlanTier tier) throws ServletException, IOException {

        ContentCachingResponseWrapper buffered = new ContentCachingResponseWrapper(response);
        chain.doFilter(request, buffered);

        byte[] body = buffered.getContentAsByteArray();
        String contentType = buffered.getContentType();
        boolean isJson = contentType != null && contentType.contains(MediaType.APPLICATION_JSON_VALUE);

        if (body.length == 0 || !isJson || buffered.getStatus() != HttpServletResponse.SC_OK) {
            buffered.copyBodyToResponse();
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            if (root instanceof ObjectNode node) {
                stripSentiment(node, tier);
                byte[] trimmed = objectMapper.writeValueAsBytes(node);
                response.setContentLength(trimmed.length);
                response.getOutputStream().write(trimmed);
                return;
            }
        } catch (Exception e) {
            // Never lose a response because filtering it failed. The worst case is that a reader
            // sees a score they have not paid for; losing the news entirely is worse.
            logger.warn("Could not trim {} - passing it through: {}", request.getRequestURI(), e.getMessage());
        }

        buffered.copyBodyToResponse();
    }

    /**
     * Removes what the tier has not paid for.
     *
     * <p>Important News needs Basic; everything sentiment needs Advanced, including the per-article
     * scores inside the free headline list — the column is what is sold, not the page it sits on.
     */
    private void stripSentiment(ObjectNode root, PlanTier tier) {
        if (!tier.atLeast(PlanTier.BASIC)) {
            root.remove("importantNews");
        }
        if (!tier.atLeast(PlanTier.ADVANCED)) {
            root.remove("latestSentiment");
            root.remove("sentimentWindows");
            root.remove("sentiments");
            stripArticleScores(root.get("news"));
            stripArticleScores(root.get("content"));     // the merged feed is a Spring Page
            stripArticleScores(root.get("importantNews"));
        }
    }

    private void stripArticleScores(JsonNode articles) {
        if (!(articles instanceof ArrayNode array)) return;
        for (JsonNode article : array) {
            if (article instanceof ObjectNode item) {
                item.remove("sentimentScore");
                item.remove("sentimentLabel");
            }
        }
    }
}
