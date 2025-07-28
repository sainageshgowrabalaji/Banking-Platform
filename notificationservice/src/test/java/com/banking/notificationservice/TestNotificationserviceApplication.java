package com.banking.notificationservice;

import org.springframework.boot.SpringApplication;

public class TestNotificationserviceApplication {

	public static void main(String[] args) {
		SpringApplication.from(NotificationserviceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
