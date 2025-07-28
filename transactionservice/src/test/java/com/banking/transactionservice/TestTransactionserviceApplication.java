package com.banking.transactionservice;

import org.springframework.boot.SpringApplication;

public class TestTransactionserviceApplication {

	public static void main(String[] args) {
		SpringApplication.from(TransactionserviceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
