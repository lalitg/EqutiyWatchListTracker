package com.companynews.newsscheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling  // ← ADD THIS — without this, @Scheduled annotations do nothing
public class NseNewsSchedulerApplication {
    public static void main(String[] args) {
        SpringApplication.run(NseNewsSchedulerApplication.class, args);
    }
}
