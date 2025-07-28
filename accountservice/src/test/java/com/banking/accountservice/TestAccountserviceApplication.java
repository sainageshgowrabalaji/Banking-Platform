package com.banking.accountservice;

import org.springframework.boot.SpringApplication;

public class TestAccountserviceApplication {

	public static void main(String[] args) {
		SpringApplication.from(AccountserviceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
