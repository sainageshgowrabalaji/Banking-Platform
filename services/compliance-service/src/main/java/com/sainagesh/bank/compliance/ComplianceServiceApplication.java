package com.sainagesh.bank.compliance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** KYC onboarding, sanctions screening, AML rules, analyst cases, audit log, trade surveillance. */
@SpringBootApplication
public class ComplianceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ComplianceServiceApplication.class, args);
    }
}
