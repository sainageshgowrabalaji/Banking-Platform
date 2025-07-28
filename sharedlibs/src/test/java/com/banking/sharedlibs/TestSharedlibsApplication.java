package com.banking.sharedlibs;

import org.springframework.boot.SpringApplication;

public class TestSharedlibsApplication {

	public static void main(String[] args) {
		SpringApplication.from(SharedlibsApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
