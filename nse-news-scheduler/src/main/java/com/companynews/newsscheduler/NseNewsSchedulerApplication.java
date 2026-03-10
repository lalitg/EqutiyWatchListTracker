package com.companynews.newsscheduler;
 
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
 
// @SpringBootApplication = auto-configuration + component scanning
// @EnableScheduling = activates the @Scheduled annotation support
@SpringBootApplication
@EnableScheduling   // ← ADD THIS LINE
public class NseNewsSchedulerApplication {
 
    public static void main(String[] args) {
        SpringApplication.run(NseNewsSchedulerApplication.class, args);
    }
}
