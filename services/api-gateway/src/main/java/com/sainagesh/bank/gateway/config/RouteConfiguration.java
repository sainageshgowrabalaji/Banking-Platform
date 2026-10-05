package com.sainagesh.bank.gateway.config;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.stripPrefix;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.filter.Bucket4jFilterFunctions.rateLimit;
import static org.springframework.cloud.gateway.server.mvc.filter.CircuitBreakerFilterFunctions.circuitBreaker;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.path;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.caffeine.CaffeineProxyManager;
import io.github.bucket4j.distributed.proxy.AsyncProxyManager;
import io.github.bucket4j.distributed.remote.RemoteBucketState;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import java.net.URI;
import java.security.Principal;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * The routes of the gateway, and the three protections every route gets.
 *
 * <p>A request to {@code /ledger/v1/accounts} matches the {@code ledger} route. The first path segment
 * is removed and the rest, {@code /v1/accounts}, is sent to the ledger service. On the way it passes
 * through these filters, outermost first.
 *
 * <ol>
 *   <li><b>Rate limit.</b> Each client has a bucket of tokens that refills over time. A request takes
 *       one token. An empty bucket means 429 Too Many Requests, and the service behind is never called.
 *       This protects every service from one client that sends too much
 *   <li><b>Circuit breaker with a timeout.</b> A call that takes too long is given up. When a service
 *       keeps failing, the breaker opens and the gateway answers 503 at once for a while, instead of
 *       making every caller wait for another timeout
 * </ol>
 */
@Configuration
@EnableConfigurationProperties(GatewayProperties.class)
public class RouteConfiguration {

    @Bean
    RouterFunction<ServerResponse> serviceRoutes(GatewayProperties properties) {
        RouterFunction<ServerResponse> routes = null;
        for (Map.Entry<String, String> service : properties.services().entrySet()) {
            String name = service.getKey();
            RouterFunction<ServerResponse> one = route(name)
                    .route(path("/" + name + "/**"), http())
                    .before(uri(service.getValue()))
                    .before(stripPrefix(1))
                    .filter(rateLimit(limit -> limit.setCapacity(properties.rateLimit().capacity())
                            .setPeriod(properties.rateLimit().period())
                            .setKeyResolver(RouteConfiguration::clientKey)))
                    .filter(circuitBreaker(name, URI.create("forward:/fallback/" + name)))
                    .build();
            routes = routes == null ? one : routes.and(one);
        }
        if (routes == null) {
            throw new IllegalStateException("No services are configured under bank.gateway.services");
        }
        return routes;
    }

    /**
     * Who a request counts against. A signed-in user is counted by name, wherever they call from.
     * Anyone else is counted by network address.
     */
    static String clientKey(ServerRequest request) {
        Principal user = request.servletRequest().getUserPrincipal();
        return user != null ? "user:" + user.getName() : "ip:" + request.servletRequest().getRemoteAddr();
    }

    /**
     * Where the rate limit buckets are kept. Here that is this gateway's own memory, which is right for
     * one copy of the gateway. With several copies the buckets move to Redis, so the copies share one
     * count. Only this bean changes.
     */
    @Bean
    @SuppressWarnings({"rawtypes", "unchecked"})
    AsyncProxyManager<String> rateLimitBuckets() {
        Caffeine<String, RemoteBucketState> cache = (Caffeine) Caffeine.newBuilder().maximumSize(100_000);
        return new CaffeineProxyManager<>(cache, Duration.ofMinutes(10)).asAsync();
    }

    /**
     * The settings of every circuit breaker.
     *
     * <p>A breaker watches the last 20 calls to its service. When half of them failed it opens for ten
     * seconds. It then lets three calls through as a test, and closes if they work. The time limiter
     * is the timeout on each call.
     */
    @Bean
    Customizer<Resilience4JCircuitBreakerFactory> circuitBreakerDefaults(GatewayProperties properties) {
        CircuitBreakerConfig breaker = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(3)
                .build();
        TimeLimiterConfig timeLimiter = TimeLimiterConfig.custom()
                .timeoutDuration(properties.timeout())
                .build();
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(breaker)
                .timeLimiterConfig(timeLimiter)
                .build());
    }
}
