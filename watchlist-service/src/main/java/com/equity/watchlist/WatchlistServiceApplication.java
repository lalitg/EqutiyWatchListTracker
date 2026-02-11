package com.equity.watchlist;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class WatchlistServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(WatchlistServiceApplication.class, args);
	}

}
