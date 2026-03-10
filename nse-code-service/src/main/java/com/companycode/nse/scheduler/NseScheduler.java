package com.companycode.nse.scheduler;

import com.companycode.nse.service.NseSyncService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NseScheduler {

    private final NseSyncService service;

    public NseScheduler(NseSyncService service) {
        this.service = service;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void runAtStartup() {
        service.syncCompanies();
    }

    @Scheduled(cron = "0 0 6 ? * SUN")
    public void runEverySunday() {
        service.syncCompanies();
    }
}
