package com.equity.sebi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SebiDataApplication {
    public static void main(String[] args) {
        SpringApplication.run(SebiDataApplication.class, args);
    }
}
