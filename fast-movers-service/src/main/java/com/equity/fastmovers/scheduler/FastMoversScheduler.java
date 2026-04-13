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

    /** Persist end-of-day close prices for all companies at 3:30 PM on weekdays. */
    @Scheduled(cron = "0 30 15 * * MON-FRI")
    public void persistDailyClose() {
        logger.info("Scheduler: persistDailyClose triggered");
        fastMoversService.persistDailyClose();
    }
}
