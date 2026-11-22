package com.equity.fastmovers;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FastMoversApplication {

    public static void main(String[] args) {
        SpringApplication.run(FastMoversApplication.class, args);
    }
}
