package com.equity.watchlist.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

/**
 * Reverse-proxy controller that forwards {@code /api/news}, {@code /api/events},
 * and {@code /api/internal} requests to their respective downstream microservices.
 *
 * <p>This allows the React frontend to be served entirely from port 8080 without
 * requiring CORS configuration or separate API origins in the browser.
 */
@RestController
public class ProxyController {

    private static final Logger logger = LogManager.getLogger(ProxyController.class);

    private final RestTemplate restTemplate = new RestTemplate();

    /** Base URL of the auth microservice (default: {@code http://localhost:8088}). */
    @Value("${auth.service.url:http://localhost:8088}")
    private String authServiceUrl;

    /** Base URL of the user microservice (default: {@code http://localhost:8087}). */
    @Value("${user.service.url:http://localhost:8087}")
    private String userServiceUrl;

    /** Base URL of the news microservice (default: {@code http://localhost:8084}). */
    @Value("${news.service.url:http://localhost:8084}")
    private String newsServiceUrl;

    /** Base URL of the events microservice (default: {@code http://localhost:8085}). */
    @Value("${events.service.url:http://localhost:8085}")
    private String eventsServiceUrl;

    /** Base URL of the global-watchlist microservice (default: {@code http://localhost:8083}). */
    @Value("${global.watchlist.service.url:http://localhost:8083}")
    private String globalWatchlistServiceUrl;

    /** Base URL of the fast-movers microservice (default: {@code http://localhost:8081}). */
    @Value("${fast.movers.service.url:http://localhost:8081}")
    private String fastMoversServiceUrl;

    /** Base URL of the fundamentals microservice (default: {@code http://localhost:8086}). */
    @Value("${fundamentals.service.url:http://localhost:8086}")
    private String fundamentalsServiceUrl;

    /** Base URL of the NSE code / enterprise-scheduler microservice (default: {@code http://localhost:8082}). */
    @Value("${nse.code.service.url:http://localhost:8082}")
    private String nseCodeServiceUrl;

    /**
     * Proxies all {@code /api/auth/**} requests to auth-service.
     * Path remapping: /api/auth/signup → /api/v1/auth/signup
     */
    @RequestMapping(value = "/api/auth/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH, RequestMethod.HEAD, RequestMethod.OPTIONS})
    public ResponseEntity<String> proxyAuth(HttpServletRequest request) throws URISyntaxException, IOException {
        logger.info("Proxying auth request: {} {}", request.getMethod(), request.getRequestURI());
        String suffix = request.getRequestURI().substring("/api/auth".length());
        return proxyWithPath(request, authServiceUrl, "/api/v1/auth" + suffix);
    }

    /**
     * Proxies all {@code /api/user/**} requests to user-service.
     * Path remapping: /api/user/me → /api/v1/users/me
     */
    @RequestMapping(value = "/api/user/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH, RequestMethod.HEAD, RequestMethod.OPTIONS})
    public ResponseEntity<String> proxyUser(HttpServletRequest request) throws URISyntaxException, IOException {
        logger.info("Proxying user request: {} {}", request.getMethod(), request.getRequestURI());
        String suffix = request.getRequestURI().substring("/api/user".length());
        return proxyWithPath(request, userServiceUrl, "/api/v1/users" + suffix);
    }

    /**
     * Proxies all {@code /api/news/**} requests to the news microservice.
     *
     * @param request the incoming HTTP request
     * @return the response from the news microservice
     * @throws URISyntaxException if the constructed target URL is malformed
     */
    @RequestMapping(value = "/api/news/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH, RequestMethod.HEAD, RequestMethod.OPTIONS})
    public ResponseEntity<String> proxyNews(HttpServletRequest request) throws URISyntaxException, IOException {
        logger.info("Proxying news request: {} {}", request.getMethod(), request.getRequestURI());
        return proxy(request, newsServiceUrl);
    }

    /**
     * Proxies all {@code /api/events/**} requests to the events microservice.
     *
     * @param request the incoming HTTP request
     * @return the response from the events microservice
     * @throws URISyntaxException if the constructed target URL is malformed
     */
    @RequestMapping(value = "/api/events/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH, RequestMethod.HEAD, RequestMethod.OPTIONS})
    public ResponseEntity<String> proxyEvents(HttpServletRequest request) throws URISyntaxException, IOException {
        logger.info("Proxying events request: {} {}", request.getMethod(), request.getRequestURI());
        return proxy(request, eventsServiceUrl);
    }

    /**
     * Proxies all {@code /api/global-watchlist/**} requests to the global-watchlist microservice.
     *
     * @param request the incoming HTTP request
     * @return the response from the global-watchlist microservice
     * @throws URISyntaxException if the constructed target URL is malformed
     */
    @RequestMapping(value = "/api/global-watchlist/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH, RequestMethod.HEAD, RequestMethod.OPTIONS})
    public ResponseEntity<String> proxyGlobalWatchlist(HttpServletRequest request) throws URISyntaxException, IOException {
        logger.info("Proxying global-watchlist request: {} {}", request.getMethod(), request.getRequestURI());
        return proxy(request, globalWatchlistServiceUrl);
    }

    /**
     * Proxies all {@code /api/fast-movers/**} requests to the fast-movers microservice.
     *
     * @param request the incoming HTTP request
     * @return the response from the fast-movers microservice
     * @throws URISyntaxException if the constructed target URL is malformed
     */
    @RequestMapping(value = "/api/fast-movers/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH, RequestMethod.HEAD, RequestMethod.OPTIONS})
    public ResponseEntity<String> proxyFastMovers(HttpServletRequest request) throws URISyntaxException, IOException {
        logger.info("Proxying fast-movers request: {} {}", request.getMethod(), request.getRequestURI());
        return proxy(request, fastMoversServiceUrl);
    }

    /**
     * Proxies all {@code /api/fundamentals/**} requests to the fundamentals microservice.
     */
    @RequestMapping(value = "/api/fundamentals/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH, RequestMethod.HEAD, RequestMethod.OPTIONS})
    public ResponseEntity<String> proxyFundamentals(HttpServletRequest request) throws URISyntaxException, IOException {
        logger.info("Proxying fundamentals request: {} {}", request.getMethod(), request.getRequestURI());
        return proxy(request, fundamentalsServiceUrl);
    }

    /**
     * Proxies all {@code /api/nse-code/**} requests to the NSE enterprise-scheduler microservice.
     */
    @RequestMapping(value = "/api/nse-code/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH, RequestMethod.HEAD, RequestMethod.OPTIONS})
    public ResponseEntity<String> proxyNseCode(HttpServletRequest request) throws URISyntaxException, IOException {
        logger.info("Proxying nse-code request: {} {}", request.getMethod(), request.getRequestURI());
        return proxy(request, nseCodeServiceUrl);
    }

    /**
     * Proxies all {@code /api/internal/**} requests to the news microservice.
     * Used for internal watchlist-added notifications consumed by the news service.
     *
     * @param request the incoming HTTP request
     * @return the response from the news microservice
     * @throws URISyntaxException if the constructed target URL is malformed
     */
    @RequestMapping(value = "/api/internal/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH, RequestMethod.HEAD, RequestMethod.OPTIONS})
    public ResponseEntity<String> proxyInternal(HttpServletRequest request) throws URISyntaxException, IOException {
        logger.info("Proxying internal request: {} {}", request.getMethod(), request.getRequestURI());
        return proxy(request, newsServiceUrl);
    }

    /**
     * Forwards an incoming request to the given target base URL, preserving
     * the original path and query string.
     *
     * @param request    the original incoming HTTP request
     * @param targetBase the base URL of the downstream service (e.g. {@code http://localhost:8084})
     * @return the downstream service's response
     * @throws URISyntaxException if the constructed target URL is malformed
     * @throws IOException        if reading the request body fails
     */
    private ResponseEntity<String> proxy(HttpServletRequest request, String targetBase)
            throws URISyntaxException, IOException {
        String queryString = request.getQueryString();
        String path = request.getRequestURI();
        String targetUrl = targetBase + path + (queryString != null ? "?" + queryString : "");

        logger.debug("Forwarding to: {}", targetUrl);

        // Read and forward the request body so POST/PUT payloads reach the downstream service
        String body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");

        HttpEntity<String> entity = new HttpEntity<>(body.isEmpty() ? null : body, headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    new URI(targetUrl), HttpMethod.valueOf(request.getMethod()), entity, String.class);
            logger.debug("Proxy response status: {}", response.getStatusCode());
            HttpHeaders cleaned = new HttpHeaders();
            response.getHeaders().forEach((name, values) -> {
                String lower = name.toLowerCase();
                if (!lower.equals("transfer-encoding") && !lower.equals("connection")) {
                    cleaned.addAll(name, values);
                }
            });
            return ResponseEntity.status(response.getStatusCode()).headers(cleaned).body(response.getBody());
        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode()).body(ex.getResponseBodyAsString());
        }
    }

    /** Proxy with an explicit downstream path — used when the path prefix must be remapped. */
    private ResponseEntity<String> proxyWithPath(HttpServletRequest request, String targetBase, String downstreamPath)
            throws URISyntaxException, IOException {
        String queryString = request.getQueryString();
        String targetUrl = targetBase + downstreamPath + (queryString != null ? "?" + queryString : "");
        logger.debug("Forwarding to: {}", targetUrl);

        String body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        java.util.Collections.list(request.getHeaderNames()).forEach(name -> {
            String lower = name.toLowerCase();
            if (!lower.equals("host") && !lower.equals("transfer-encoding") && !lower.equals("connection")) {
                headers.set(name, request.getHeader(name));
            }
        });

        HttpEntity<String> entity = new HttpEntity<>(body.isEmpty() ? null : body, headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    new URI(targetUrl), HttpMethod.valueOf(request.getMethod()), entity, String.class);
            HttpHeaders cleaned = new HttpHeaders();
            response.getHeaders().forEach((name, values) -> {
                String lower = name.toLowerCase();
                if (!lower.equals("transfer-encoding") && !lower.equals("connection")) {
                    cleaned.addAll(name, values);
                }
            });
            return ResponseEntity.status(response.getStatusCode()).headers(cleaned).body(response.getBody());
        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode()).body(ex.getResponseBodyAsString());
        }
    }
}
