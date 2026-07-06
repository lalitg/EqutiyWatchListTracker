package com.companynews.newsscheduler.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Central Spring configuration class that declares all shared infrastructure beans.
 *
 * <p>Beans declared here are singletons shared across the entire application context:
 * <ul>
 *   <li>{@link ObjectMapper} — thread-safe JSON serializer/deserializer with JSR-310 support.</li>
 *   <li>{@link HttpClient} — singleton HTTP client with connection pooling and timeouts.</li>
 *   <li>{@code googleRssExecutor} — fixed thread pool for parallel Google RSS keyword fetching.</li>
 *   <li>{@code nseTaskScheduler} — dedicated single-thread scheduler for NSE polling.</li>
 *   <li>{@code startupScheduler} — lightweight scheduler for one-shot startup delays.</li>
 * </ul>
 */
@Configuration
@EnableCaching
@EnableAsync
public class AppConfig {

    private static final Logger log = LogManager.getLogger(AppConfig.class);

    /**
     * Configures the application-wide Jackson {@link ObjectMapper}.
     *
     * <p>Registers the {@link JavaTimeModule} so that {@link java.time.LocalDateTime} and
     * other JSR-310 types are serialized as ISO-8601 strings rather than epoch timestamps.
     * {@code WRITE_DATES_AS_TIMESTAMPS} is explicitly disabled as a safety net in case
     * the property-level setting is overridden elsewhere.
     *
     * @return a fully configured, thread-safe {@link ObjectMapper} instance
     */
    @Bean
    public ObjectMapper objectMapper() {
        log.debug("Initializing ObjectMapper with JavaTimeModule");
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    /**
     * Singleton shared {@link HttpClient} for all outbound HTTP calls (NSE + Google RSS).
     *
     * <p>WHY singleton: Java's {@code HttpClient} maintains an internal connection pool and
     * thread pool. Creating a new {@code HttpClient} per request means:
     * <ul>
     *   <li>No connection reuse — every call pays TCP handshake cost.</li>
     *   <li>Each instance spawns its own internal thread pool — wastes OS resources.</li>
     *   <li>No timeout enforcement — threads hang indefinitely on slow external APIs.</li>
     * </ul>
     *
     * <p>With a singleton:
     * <ul>
     *   <li>Connections to the same host (NSE/Google) are reused across calls.</li>
     *   <li>One shared internal thread pool for I/O multiplexing.</li>
     *   <li>{@code connectTimeout} enforced: if NSE server doesn't respond in N seconds, fail fast.</li>
     *   <li>{@code FOLLOW_REDIRECTS}: transparently follows HTTP 301/302 from Google.</li>
     * </ul>
     *
     * <p>Request-level read timeout is set on each {@code HttpRequest.Builder} separately
     * (see {@link com.companynews.newsscheduler.service.NseFetcher} and
     * {@link com.companynews.newsscheduler.service.RssFetcher}) because timeout per-request
     * lets us tune NSE vs. RSS independently if needed in the future.
     *
     * @param connectTimeoutSeconds max seconds to wait for a TCP connection (from {@code news.http.connect-timeout-seconds})
     * @return a configured singleton {@link HttpClient}
     */
    @Bean
    public HttpClient httpClient(
            @Value("${news.http.connect-timeout-seconds:5}") int connectTimeoutSeconds) {
        log.info("Initializing HttpClient with connectTimeout={}s", connectTimeoutSeconds);
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    /**
     * Managed fixed thread pool for Google RSS keyword fetching.
     *
     * <p>WHY a managed bean instead of creating inside the scheduler:
     * The old pattern called {@code Executors.newFixedThreadPool(n)} inside the scheduler
     * method and then called {@code executor.shutdown()} at the end. This meant:
     * <ul>
     *   <li>Thread creation overhead on every scheduler run (threads are expensive to spawn).</li>
     *   <li>If shutdown raced with a running task, tasks could be cut off mid-save.</li>
     *   <li>No visibility of the pool in thread dumps (pool had no name).</li>
     * </ul>
     *
     * <p>As a Spring-managed bean:
     * <ul>
     *   <li>Pool is created ONCE at startup and reused across all scheduler runs.</li>
     *   <li>{@code destroyMethod = "shutdown"} ensures graceful draining at app termination.</li>
     *   <li>Named threads ({@code rss-worker-N}) are visible in thread dumps and logs.</li>
     * </ul>
     *
     * @param poolSize number of worker threads (from {@code news.google.thread-pool-size})
     * @return a named fixed-size {@link ExecutorService}
     */
    @Bean(name = "googleRssExecutor", destroyMethod = "shutdown")
    public ExecutorService googleRssExecutor(
            @Value("${news.google.thread-pool-size:2}") int poolSize) {
        log.info("Initializing googleRssExecutor with poolSize={}", poolSize);
        AtomicInteger counter = new AtomicInteger(1);
        ThreadFactory namedFactory = r -> {
            Thread t = new Thread(r, "rss-worker-" + counter.getAndIncrement());
            t.setDaemon(false);
            return t;
        };
        return Executors.newFixedThreadPool(poolSize, namedFactory);
    }

    /**
     * Dedicated single-thread {@link TaskScheduler} for the NSE scheduled fetch.
     *
     * <p>WHY a dedicated {@code TaskScheduler}:
     * By default Spring uses one shared scheduler thread pool for ALL {@code @Scheduled}
     * methods across the entire application. If any scheduled task hangs or takes too long,
     * it blocks all other tasks.
     *
     * <p>A dedicated scheduler for NSE means:
     * <ul>
     *   <li>NSE always has its own thread — never blocked by Google RSS or other tasks.</li>
     *   <li>Thread is named {@code nse-scheduler-thread-1} — visible in logs and thread dumps.</li>
     *   <li>Easy to identify NSE activity in monitoring tools.</li>
     * </ul>
     *
     * <p>{@code poolSize(1)} is intentional — NSE rate-limits aggressive polling.
     * Do NOT increase without confirming NSE allows concurrent requests.
     *
     * @return a single-threaded {@link TaskScheduler} named {@code nseTaskScheduler}
     */
    @Bean(name = "nseTaskScheduler")
    public TaskScheduler nseTaskScheduler() {
        log.info("Initializing nseTaskScheduler (poolSize=1)");
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("nse-scheduler-thread-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();
        return scheduler;
    }

    /**
     * Lightweight single-thread {@link TaskScheduler} for one-off startup delays.
     *
     * <p>WHY a managed bean instead of raw {@code new Thread()}:
     * The old pattern used:
     * <pre>{@code new Thread(() -> { Thread.sleep(5000); runFetch(); }, "rss-startup-thread").start()}</pre>
     *
     * <p>Problems with raw {@code Thread}:
     * <ul>
     *   <li>Unmanaged: Spring cannot monitor, interrupt, or wait for it on shutdown.</li>
     *   <li>If the app stops during the 5-second delay the thread is abandoned silently.</li>
     *   <li>No visibility in thread dumps beyond the raw name.</li>
     * </ul>
     *
     * <p>With a managed {@code TaskScheduler} bean:
     * <ul>
     *   <li>Spring owns the lifecycle — {@code destroyMethod = "shutdown"} drains on app stop.</li>
     *   <li>Thread is named {@code startup-thread-1}, visible in metrics and thread dumps.</li>
     *   <li>{@code taskScheduler.schedule(runnable, Instant)} is the correct Spring 6 API for
     *       one-shot delayed execution — no manual sleep or interrupt handling needed.</li>
     * </ul>
     *
     * @return a single-threaded {@link TaskScheduler} named {@code startupScheduler}
     */
    @Bean(name = "startupScheduler", destroyMethod = "shutdown")
    public TaskScheduler startupScheduler() {
        log.info("Initializing startupScheduler (poolSize=1)");
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("startup-thread-");
        scheduler.initialize();
        return scheduler;
    }

    @Bean
    public CacheManager cacheManager() {
        log.info("Initializing CaffeineCacheManager (mergedNews + companyNews)");
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.registerCustomCache("mergedNews",
            Caffeine.newBuilder().maximumSize(500).recordStats().<Object, Object>build());
        manager.registerCustomCache("companyNews",
            Caffeine.newBuilder().maximumSize(100).recordStats().<Object, Object>build());
        return manager;
    }

    @Bean(name = "cacheWarmExecutor")
    public ThreadPoolTaskExecutor cacheWarmExecutor() {
        log.info("Initializing cacheWarmExecutor (poolSize=2)");
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setThreadNamePrefix("cache-warm-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
