package com.sainagesh.bank.testing;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Marks a test that needs a real PostgreSQL and a real Kafka.
 *
 * <p>When Docker is running, the test starts both in containers. When it is not, and no outside servers
 * are named either, the test is skipped with a clear reason. It is never a failure to have no Docker.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@ExtendWith(InfrastructureCondition.class)
public @interface RequiresInfrastructure {}
