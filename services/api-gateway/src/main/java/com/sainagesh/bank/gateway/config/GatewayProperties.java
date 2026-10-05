package com.sainagesh.bank.gateway.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the gateway, under {@code bank.gateway}.
 *
 * @param services where each service lives, by route name. A request to {@code /ledger/...} goes to
 *                 the address under {@code ledger}. Docker and Kubernetes set these addresses with
 *                 environment variables
 * @param timeout  how long the gateway waits for a service before it gives up on the call
 */
@ConfigurationProperties("bank.gateway")
public record GatewayProperties(Map<String, String> services, Security security, RateLimit rateLimit, Duration timeout) {

    public GatewayProperties {
        services = services == null ? Map.of() : new LinkedHashMap<>(services);
        security = security == null ? new Security(null, null) : security;
        rateLimit = rateLimit == null ? new RateLimit(null, null) : rateLimit;
        timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
    }

    /**
     * @param enabled      false lets every request through with no token. For a laptop with no Keycloak only
     * @param operatorRole the role that may call internal endpoints, such as posting a journal entry
     */
    public record Security(Boolean enabled, String operatorRole) {

        public Security {
            enabled = enabled == null ? Boolean.TRUE : enabled;
            operatorRole = operatorRole == null ? "bank-operator" : operatorRole;
        }
    }

    /** Each client may make {@code capacity} requests in each {@code period}. */
    public record RateLimit(Long capacity, Duration period) {

        public RateLimit {
            capacity = capacity == null ? 120L : capacity;
            period = period == null ? Duration.ofMinutes(1) : period;
        }
    }
}
