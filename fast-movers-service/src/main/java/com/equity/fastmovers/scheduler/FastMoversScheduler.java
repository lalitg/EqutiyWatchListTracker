package com.equity.fastmovers.scheduler;

import com.equity.fastmovers.service.FastMoversService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FastMoversScheduler {

    private static final Logger logger = LogManager.getLogger(FastMoversScheduler.class);

    private final FastMoversService fastMoversService;

    public FastMoversScheduler(FastMoversService fastMoversService) {
        this.fastMoversService = fastMoversService;
    }

    /** Refresh today's gainers/losers every hour on weekdays. */
    @Scheduled(cron = "0 0 * * * MON-FRI")
    public void refreshTodayMovers() {
        logger.info("Scheduler: refreshTodayMovers triggered");
        fastMoversService.refreshTodayMovers();
    }

    /** Persist previous trading day's close prices at 9:30 AM on weekdays. */
    @Scheduled(cron = "0 30 9 * * MON-FRI")
    public void persistDailyClose() {
        logger.info("Scheduler: persistDailyClose triggered");
        fastMoversService.persistDailyClose();
    }
}
