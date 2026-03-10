package com.companycode.nse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NseEnterpriseSchedulerApplication {

    public static void main(String[] args) {
        SpringApplication.run(NseEnterpriseSchedulerApplication.class, args);
    }
}
