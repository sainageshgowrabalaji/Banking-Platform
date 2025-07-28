package com.banking.configserver;

import org.springframework.boot.SpringApplication;

public class TestConfigserverApplication {

	public static void main(String[] args) {
		SpringApplication.from(ConfigserverApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
