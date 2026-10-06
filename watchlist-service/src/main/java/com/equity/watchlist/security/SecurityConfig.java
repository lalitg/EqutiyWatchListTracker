package com.equity.watchlist.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration for watchlist-service.
 *
 * Public paths (no JWT required):
 *   - /api/auth/**   — proxied to auth-service (signup, login, refresh, logout)
 *   - /api/user/**   — proxied to user-service (register)
 *   - /api/v1/companies/** — company search (read-only, no user context needed)
 *   - /**            — frontend SPA static assets
 *
 * Protected (JWT required):
 *   - /api/v1/watchlist/** — all watchlist operations
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .formLogin(fl -> fl.disable())
            .httpBasic(hb -> hb.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/user/**").permitAll()
                .requestMatchers("/api/v1/companies/**").permitAll()
                .requestMatchers("/api/v1/watchlist/**").authenticated()
                // Alert subscriptions belong to the signed-in user; the path never carries a user
                // id, so a caller can only ever change their own.
                .requestMatchers("/api/v1/subscriptions/**").authenticated()
                // The unsubscribe link is opened from a mail client with no session. Its signed
                // token is the credential — requiring a login here would mean signing in to stop
                // unwanted email, and the reliable alternative a reader reaches for is "spam".
                .requestMatchers("/api/alerts/**").permitAll()
                .anyRequest().permitAll()
            )
            .exceptionHandling(eh -> eh
                .authenticationEntryPoint((req, res, ex) -> {
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"error\":\"Unauthorized\"}");
                })
                .accessDeniedHandler((req, res, ex) -> {
                    res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"error\":\"Forbidden\"}");
                })
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
