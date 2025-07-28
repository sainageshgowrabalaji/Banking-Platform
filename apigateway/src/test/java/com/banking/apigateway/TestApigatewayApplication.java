package com.banking.apigateway;

import org.springframework.boot.SpringApplication;

public class TestApigatewayApplication {

	public static void main(String[] args) {
		SpringApplication.from(ApigatewayApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
