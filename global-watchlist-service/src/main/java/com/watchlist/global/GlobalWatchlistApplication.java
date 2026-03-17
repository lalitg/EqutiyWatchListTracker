package com.watchlist.global;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GlobalWatchlistApplication {

    public static void main(String[] args) {
        SpringApplication.run(GlobalWatchlistApplication.class, args);
    }
}
