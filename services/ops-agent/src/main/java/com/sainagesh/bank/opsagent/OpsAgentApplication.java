package com.sainagesh.bank.opsagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** AI operations agent with read-only tools over logs, traces, tests and scan reports. */
@SpringBootApplication
public class OpsAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpsAgentApplication.class, args);
    }
}
