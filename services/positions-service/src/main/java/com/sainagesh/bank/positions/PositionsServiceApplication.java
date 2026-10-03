package com.sainagesh.bank.positions;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Positions built from fills, realised and unrealised P&L, VaR, Greeks, end-of-day batch. */
@SpringBootApplication
public class PositionsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PositionsServiceApplication.class, args);
    }
}
