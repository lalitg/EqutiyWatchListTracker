package com.unified;

import com.companynews.newsscheduler.NseNewsSchedulerApplication;
import com.companycode.nse.NseEnterpriseSchedulerApplication;
import com.equity.fastmovers.FastMoversApplication;
import com.nseevents.nse_events_scheduler.NseEventsSchedulerApplication;
import com.watchlist.global.GlobalWatchlistApplication;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.util.concurrent.CountDownLatch;

/**
 * Single-JVM launcher for all 5 equity watchlist scheduler services.
 *
 * <p><b>Approach — Multi-Context Bootstrap:</b> Each service is started via
 * {@link SpringApplicationBuilder} on its own dedicated thread. Spring creates
 * a fully independent {@code ApplicationContext} per service, so each service
 * has its own beans, DataSource/HikariCP pool, Flyway instance, and scheduler
 * thread pool. The only things shared at JVM level are the heap, class loader,
 * and Log4j2 (which is JVM-global and configured once from
 * {@code log4j2-spring.xml} in this module's resources).
 *
 * <p><b>Config isolation:</b> Each service context is launched with
 * {@code spring.config.name=application-{service}}, so Spring Boot loads the
 * named properties file from this module's classpath instead of the default
 * {@code application.properties}. This avoids classpath config collisions when
 * all 5 service JARs are on the same classpath.
 *
 * <p><b>Port assignment:</b>
 * <ul>
 *   <li>global-watchlist  → 8083</li>
 *   <li>nse-code          → 8082</li>
 *   <li>nse-events        → 8085</li>
 *   <li>nse-news          → 8084 (watchlist-service expects nse-news on 8084)</li>
 *   <li>fast-movers       → 8081</li>
 * </ul>
 *
 * <p><b>This class is intentionally NOT a {@code @SpringBootApplication}.</b>
 * Adding that annotation would create a 5th context that component-scans all
 * packages and conflicts with the 4 service contexts. A plain Java main is
 * sufficient — {@code SpringApplicationBuilder} is a library class and does
 * not require the caller to be a Spring bean.
 */
public class UnifiedSchedulerApplication {

    private static final Logger log = LogManager.getLogger(UnifiedSchedulerApplication.class);

    /**
     * Entry point. Initialises Log4j2 for the entire JVM, then starts all
     * 4 service contexts on dedicated non-daemon threads. The main thread
     * blocks on a {@link CountDownLatch} so the JVM stays alive indefinitely
     * (until the process is killed or a shutdown hook fires).
     *
     * @param args command-line arguments (not forwarded to service contexts — each
     *             service receives its own pinned {@code --spring.config.name} and
     *             {@code --server.port} command-line args)
     * @throws InterruptedException if the main thread is interrupted while waiting
     */
    public static void main(String[] args) throws InterruptedException {
        /*
         * Force Log4j2 to use the unified config before any Spring context
         * bootstraps its own logging system. Log4j2 is JVM-global — once
         * initialised by the first context, subsequent contexts' logging.config
         * values are ignored. Setting the system property here guarantees the
         * unified config wins regardless of which service thread starts first.
         */
        System.setProperty("logging.config", "classpath:log4j2-spring.xml");

        log.info("=== Unified Scheduler starting — launching 5 service contexts ===");

        startService("global-watchlist", GlobalWatchlistApplication.class,
                "application-global-watchlist", "8083");

        startService("nse-code", NseEnterpriseSchedulerApplication.class,
                "application-nse-code", "8082");

        startService("nse-events", NseEventsSchedulerApplication.class,
                "application-nse-events", "8085");

        startService("nse-news", NseNewsSchedulerApplication.class,
                "application-nse-news", "8084");

        startService("fast-movers", FastMoversApplication.class,
                "application-fast-movers", "8081");

        log.info("=== All 5 service threads launched — JVM will stay alive ===");

        /*
         * Block the main thread forever. The JVM stays alive because each
         * service thread is non-daemon (Spring Boot's internal threads are
         * non-daemon by default). This latch is never counted down — a clean
         * shutdown happens via SIGTERM / Ctrl-C which triggers Spring's
         * registered shutdown hooks on each context.
         */
        new CountDownLatch(1).await();
    }

    /**
     * Creates and starts a non-daemon thread that bootstraps one service's
     * Spring {@code ApplicationContext}.
     *
     * <p>The {@link SpringApplicationBuilder} is constructed inside the thread
     * so it is fully owned and never shared across threads.
     *
     * <p><b>Why {@code --key=value} args instead of {@code .properties()}:</b>
     * Spring Boot's documentation states that {@code spring.config.name} and
     * {@code server.port} must be "environment properties" (system property,
     * OS env variable, or command-line argument). {@code .properties()} sets
     * them as {@code defaultProperties} — the <em>lowest</em> priority source,
     * processed after config data is loaded. That is too late for
     * {@code spring.config.name} to influence which file Spring Boot reads,
     * so it is silently ignored and Spring Boot falls back to
     * {@code application.properties}. With 4 service JARs each shipping an
     * {@code application.properties} on the shared classpath, all 4 contexts
     * end up loading the wrong file. Passing them as {@code --key=value} makes
     * them command-line arguments — the <em>highest</em> priority source,
     * processed before config data loading — which correctly selects the named
     * file and pins the port regardless of what the loaded file says.
     *
     * @param name       human-readable service name used in thread name and logs
     * @param appClass   the service's {@code @SpringBootApplication} class
     * @param configName base name of the properties file to load
     *                   (e.g. {@code "application-nse-news"})
     * @param port       HTTP port for this service's embedded Tomcat
     */
    private static void startService(String name, Class<?> appClass,
                                     String configName, String port) {
        Thread thread = new Thread(() -> {
            try {
                log.info("Launching context: {}", name);
                new SpringApplicationBuilder(appClass)
                        .run(
                                "--spring.config.name=" + configName,
                                "--server.port=" + port
                        );
                log.info("Context ready: {}", name);
            } catch (Exception e) {
                log.error("Context failed to start: {}", name, e);
            }
        }, "ctx-" + name);

        // Non-daemon: ensures JVM does not exit if main() somehow unblocks
        thread.setDaemon(false);
        thread.start();
    }
}
