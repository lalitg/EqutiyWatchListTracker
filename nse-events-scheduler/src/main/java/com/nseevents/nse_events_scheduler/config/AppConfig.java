package com.nseevents.nse_events_scheduler.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Application-level bean configuration.
 */
@Configuration
public class AppConfig {

    /**
     * Customizes Spring Boot's auto-configured {@code ObjectMapper}.
     *
     * <p>Using {@link Jackson2ObjectMapperBuilderCustomizer} is preferred over
     * defining a raw {@code ObjectMapper} bean, because it augments Spring Boot's
     * shared mapper rather than replacing it — avoiding conflicts with HTTP message
     * converters and other framework components that depend on the same instance.</p>
     *
     * <p>Registers {@link JavaTimeModule} so {@code LocalDateTime} fields serialize
     * as ISO-8601 strings (e.g. {@code "2026-03-24T07:00:00"}) instead of numeric
     * timestamp arrays.</p>
     *
     * @return customizer applied to the shared Jackson mapper at startup
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder
            .modules(new JavaTimeModule())
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
