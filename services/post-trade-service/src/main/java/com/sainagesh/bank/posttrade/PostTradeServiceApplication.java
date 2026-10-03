package com.sainagesh.bank.posttrade;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Allocations, T+1 settlement, breaks and the audit-trail export. */
@SpringBootApplication
public class PostTradeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PostTradeServiceApplication.class, args);
    }
}
