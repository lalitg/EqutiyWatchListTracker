package com.nseevents.nse_events_scheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NseEventsSchedulerApplication {
    public static void main(String[] args) {
        SpringApplication.run(NseEventsSchedulerApplication.class, args);
    }
}
