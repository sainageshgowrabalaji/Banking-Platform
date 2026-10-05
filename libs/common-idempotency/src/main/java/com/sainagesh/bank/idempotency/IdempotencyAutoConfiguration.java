package com.sainagesh.bank.idempotency;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

/** Gives any service with a database the idempotency store. The service adds the table to its schema. */
@AutoConfiguration(
        afterName = {
            "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration",
            "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration"
        })
public class IdempotencyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(JdbcClient.class)
    IdempotencyStore idempotencyStore(JdbcClient jdbc) {
        return new IdempotencyStore(jdbc);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(JsonMapper.class)
    RequestHash requestHash(JsonMapper mapper) {
        return new RequestHash(mapper);
    }
}
