package com.equity.watchlist.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * SPA (Single Page Application) fallback controller for React Router support.
 *
 * <p>Problem: When a user refreshes the browser on a React Router path (e.g. /market/nifty50),
 * Spring Boot looks for a backend mapping for that path, finds none, and returns a 404.
 *
 * <p>Solution: Implement {@link ErrorController} to intercept Spring Boot's /error route.
 * When Spring Boot cannot match a request, it internally redirects to /error — we catch
 * that here and forward to index.html, letting React Router handle the route on the client side.
 *
 * <p>Why ErrorController instead of a catch-all @RequestMapping:
 * A catch-all controller pattern (e.g. "/**") intercepts ALL requests including static resources
 * (/static/js/main.js, /static/css/main.css), causing the browser to receive HTML instead of
 * JS/CSS and breaking the app with "Unexpected token '<'" errors.
 * ErrorController only fires for genuinely unmatched paths — static files are served
 * successfully by Spring Boot's resource handler and never reach /error.
 */
@Controller
public class SpaController implements ErrorController {

    /**
     * Handles all errors routed through Spring Boot's /error endpoint.
     *
     * <p>For 404s (path not found on backend) — which is the normal case for React Router paths
     * like /watchlist, /market/nifty50, /market/fast-movers — we forward to index.html so the
     * React app boots and React Router resolves the correct page.
     *
     * @param request the incoming HTTP request, used to read the original error status code
     * @return a forward directive to index.html
     */
    @RequestMapping("/error")
    public String handleError(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (status != null && Integer.parseInt(status.toString()) == 404) {
            return "forward:/index.html";
        }
        return "forward:/index.html";
    }
}
