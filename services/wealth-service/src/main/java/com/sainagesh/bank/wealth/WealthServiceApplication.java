package com.sainagesh.bank.wealth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Model portfolios, drift rebalancing that sends orders to trading, time-weighted returns. */
@SpringBootApplication
public class WealthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WealthServiceApplication.class, args);
    }
}
