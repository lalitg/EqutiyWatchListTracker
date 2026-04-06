package com.equity.watchlist.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Global CORS configuration for the Watchlist Service.
 *
 * Why this is needed:
 * When the frontend is served over HTTPS (https://niveshflow.com), the browser
 * sends an Origin header with every API request. Without CORS configuration,
 * Spring Boot rejects all cross-origin requests by default, causing the frontend
 * to receive no data and fall back to empty/mock responses.
 *
 * This configurer allows requests from both the HTTP and HTTPS domain origins
 * so the app works correctly whether accessed via IP, HTTP domain, or HTTPS domain.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${cors.allowed.origins:http://localhost:3000,http://niveshflow.com,https://niveshflow.com,http://www.niveshflow.com,https://www.niveshflow.com}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
