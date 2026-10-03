package com.sainagesh.bank.payments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Payment lifecycle as a saga. ISO 20022 messages, book, ACH and instant rails, cut-offs. */
@SpringBootApplication
public class PaymentsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentsServiceApplication.class, args);
    }
}
