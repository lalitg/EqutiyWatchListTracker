package com.companynews.newsscheduler.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class AppConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    /**
     * Dedicated named thread pool for NSE scheduler.
     *
     * WHY a dedicated TaskScheduler:
     * By default Spring uses one shared scheduler thread pool for ALL
     * @Scheduled methods across the entire application.
     * If any scheduled task hangs or takes too long, it blocks other tasks.
     *
     * A dedicated scheduler for NSE means:
     * - NSE always has its own thread — never blocked by Google RSS or other tasks
     * - Thread is named "nse-scheduler-thread-1" — visible in logs and thread dumps
     * - Easy to identify NSE activity in monitoring tools
     *
     * poolSize(1) = exactly one thread for NSE — matches LLD requirement
     * of 1 dedicated NSE thread.
     */
    @Bean(name = "nseTaskScheduler")
    public TaskScheduler nseTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("nse-scheduler-thread-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();
        return scheduler;
    }
}
