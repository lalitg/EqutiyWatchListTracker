package com.companynews.newsscheduler.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

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
     * Singleton shared HttpClient for all outbound HTTP calls (NSE + Google RSS).
     *
     * WHY singleton:
     * Java's HttpClient maintains an internal connection pool and thread pool.
     * Creating a new HttpClient per request (the old pattern) means:
     *  - No connection reuse — every call pays TCP handshake cost
     *  - Each instance spawns its own internal thread pool — wasting resources
     *  - No timeout enforcement — threads hang indefinitely on slow external APIs
     *
     * With a singleton:
     *  - Connections to the same host (NSE/Google) are reused across calls
     *  - One shared internal thread pool for I/O multiplexing
     *  - connectTimeout enforced: if NSE server doesn't respond in 5s, fail fast
     *  - FOLLOW_REDIRECTS: transparently follows HTTP 301/302 from Google
     *
     * Request-level read timeout is set on each HttpRequest.Builder separately
     * (see NseFetchService and GoogleRssService) because timeout per-request
     * lets us tune NSE vs. RSS independently if needed in the future.
     */
    @Bean
    public HttpClient httpClient(
            @Value("${news.http.connect-timeout-seconds:5}") int connectTimeoutSeconds) {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    /**
     * Managed thread pool for Google RSS keyword fetching.
     *
     * WHY a managed bean instead of creating inside the scheduler:
     * The old code called Executors.newFixedThreadPool(n) inside fetchAndSaveGoogleRssNews(),
     * which created a brand-new pool on EVERY scheduler run and then called executor.shutdown().
     * This meant:
     *  - Thread creation overhead on every run (threads are expensive to spawn)
     *  - If shutdown raced with a running task, tasks could be cut off mid-save
     *  - No visibility of the pool in thread dumps (pool had no name)
     *
     * As a Spring-managed bean:
     *  - Pool is created ONCE at startup and reused across all scheduler runs
     *  - destroyMethod = "shutdown" ensures graceful draining at app termination
     *  - Named threads ("rss-worker-N") are visible in thread dumps and logs
     *  - @Qualifier("googleRssExecutor") prevents ambiguity if other ExecutorService beans are added
     *
     * Pool size is read from news.google.thread-pool-size (default 2).
     */
    @Bean(name = "googleRssExecutor", destroyMethod = "shutdown")
    public ExecutorService googleRssExecutor(
            @Value("${news.google.thread-pool-size:2}") int poolSize) {
        AtomicInteger counter = new AtomicInteger(1);
        ThreadFactory namedFactory = r -> {
            Thread t = new Thread(r, "rss-worker-" + counter.getAndIncrement());
            t.setDaemon(false);
            return t;
        };
        return Executors.newFixedThreadPool(poolSize, namedFactory);
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
     * poolSize(1) is intentional — NSE rate-limits aggressive polling.
     * Do NOT increase without confirming NSE allows concurrent requests.
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

    /**
     * Lightweight single-thread TaskScheduler for one-off startup delays.
     *
     * WHY a managed bean instead of raw new Thread():
     * The old GoogleRssScheduler.onStartup() used:
     *   new Thread(() -> { Thread.sleep(5000); runFetch(); }, "rss-startup-thread").start()
     *
     * Problems with raw Thread:
     *  - Unmanaged: Spring cannot monitor, interrupt, or wait for it on shutdown
     *  - If the app stops during the 5-second delay the thread is abandoned silently
     *  - No visibility in thread dumps beyond the raw name
     *
     * With a managed TaskScheduler bean:
     *  - Spring owns the lifecycle — destroyMethod = "shutdown" drains on app stop
     *  - Thread is named "startup-thread-1" and visible in metrics and thread dumps
     *  - taskScheduler.schedule(runnable, Instant) is the correct Spring 6 API for
     *    one-shot delayed execution — no manual sleep or interrupt handling needed
     */
    @Bean(name = "startupScheduler", destroyMethod = "shutdown")
    public TaskScheduler startupScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("startup-thread-");
        scheduler.initialize();
        return scheduler;
    }
}
