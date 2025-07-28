package com.banking.analyticsservice;

import org.springframework.boot.SpringApplication;

public class TestAnalyticsserviceApplication {

	public static void main(String[] args) {
		SpringApplication.from(AnalyticsserviceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
