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

    @RequestMapping(value = {
        "/",
        "/watchlist",
        "/watchlist/**",
        "/global-watchlist",
        "/global-watchlist/**",
        "/fast-movers",
        "/fast-movers/**",
        "/nifty50",
        "/nifty50/**",
        "/domestic",
        "/domestic/**",
        "/global",
        "/global/**"
    })
    public String forward(HttpServletRequest request) {
        return "forward:/index.html";
    }
}
