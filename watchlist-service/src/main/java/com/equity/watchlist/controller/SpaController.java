package com.equity.watchlist.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Forwards all non-API, non-static routes to index.html so React Router
 * can handle client-side navigation on page refresh.
 */
@Controller
public class SpaController {

    /**
     * Catch-all for any path that isn't under /api/** or a static resource.
     * Spring Boot's static resource handler takes priority for actual files
     * (index.html, JS, CSS), so this only fires for unknown paths like /market/nifty50.
     */
    @RequestMapping(value = "/{path:[^\\.]*}/**")
    public String forward(HttpServletRequest request) {
        return "forward:/index.html";
    }
}
