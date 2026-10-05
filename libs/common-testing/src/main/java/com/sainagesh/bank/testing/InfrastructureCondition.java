package com.sainagesh.bank.testing;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/** Skips a test class when there is nothing to run PostgreSQL and Kafka on. */
class InfrastructureCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        return TestInfrastructure.available()
                ? ConditionEvaluationResult.enabled(TestInfrastructure.describe())
                : ConditionEvaluationResult.disabled(
                        "Skipped. Start Docker, or set BANK_TEST_POSTGRES_URL and BANK_TEST_KAFKA to servers that are already running.");
    }
}
