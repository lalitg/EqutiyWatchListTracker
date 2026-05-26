package com.equity.fundamentals.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Application-level bean configuration.
 *
 * Provides:
 *  1. Jackson customizer — serializes LocalDate/LocalDateTime as ISO-8601 strings.
 *  2. RestTemplate — used by YFinanceWrapperClient to call the Python yfinance wrapper.
 */
@Configuration
public class AppConfig {

    /**
     * Customizes Spring Boot's auto-configured ObjectMapper.
     *
     * Using Jackson2ObjectMapperBuilderCustomizer augments the shared mapper rather than
     * replacing it — avoids conflicts with HTTP message converters that depend on the same instance.
     *
     * Registers JavaTimeModule so LocalDate/LocalDateTime serialize as ISO-8601 strings
     * (e.g. "2024-09-30") instead of numeric arrays.
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder
            .modules(new JavaTimeModule())
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * RestTemplate used by YFinanceWrapperClient to call the Python Flask wrapper.
     *
     * Uses SimpleClientHttpRequestFactory directly to avoid RestTemplateBuilder API
     * differences between Spring Boot versions (connectTimeout/readTimeout method
     * signatures changed across 3.x releases).
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(30).toMillis());
        return new RestTemplate(factory);
    }
}
