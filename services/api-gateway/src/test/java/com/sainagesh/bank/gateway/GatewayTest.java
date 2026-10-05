package com.sainagesh.bank.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sainagesh.bank.testing.HttpJson;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The gateway from the outside. A small stand-in service sits behind it, and tokens are signed with a
 * key made for the test, so no Keycloak is needed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayTest {

    private static final RSAKey KEY = newKey();
    private static final RSAKey OTHER_KEY = newKey();
    private static final List<String> SEEN = new CopyOnWriteArrayList<>();
    private static final HttpServer BACKEND = backend();

    @LocalServerPort
    int port;

    HttpJson anonymous;

    @TestConfiguration
    static class TestKeys {

        /** Checks tokens against the test key instead of asking Keycloak for its keys. */
        @Bean
        JwtDecoder jwtDecoder() throws JOSEException {
            return NimbusJwtDecoder.withPublicKey(KEY.toRSAPublicKey()).build();
        }
    }

    @DynamicPropertySource
    static void settings(DynamicPropertyRegistry registry) {
        String backend = "http://localhost:" + BACKEND.getAddress().getPort();
        registry.add("bank.gateway.services.ledger", () -> backend);
        registry.add("bank.gateway.services.wealth", () -> backend);
        // Nothing listens on port 1, so this service is "down".
        registry.add("bank.gateway.services.payments", () -> "http://localhost:1");
        registry.add("bank.gateway.timeout", () -> "PT1S");
        registry.add("bank.gateway.rate-limit.capacity", () -> "25");
    }

    @BeforeEach
    void client() {
        anonymous = new HttpJson("http://localhost:" + port);
        SEEN.clear();
    }

    private HttpJson as(String user, String... roles) {
        return anonymous.withToken(token(KEY, user, Instant.now().plusSeconds(300), roles));
    }

    @Test
    void aRequestWithNoTokenIsRefused() {
        HttpJson.Response response = anonymous.get("/ledger/v1/accounts/abc");

        assertEquals(401, response.status());
        assertEquals("UNAUTHENTICATED", response.text("/code"));
        assertTrue(SEEN.isEmpty(), "The service behind the gateway must not be called");
    }

    @Test
    void aRequestWithAValidTokenReachesTheServiceWithThePrefixRemoved() {
        HttpJson.Response response = as("alice", "customer").get("/ledger/v1/accounts/abc?limit=5");

        assertEquals(200, response.status(), response.body().toString());
        assertEquals("/v1/accounts/abc?limit=5", response.text("/path"));
    }

    @Test
    void everyRequestGetsAnIdThatIsPassedOnAndReturned() {
        HttpJson.Response response = as("alice", "customer").get("/ledger/v1/accounts/abc");

        String id = response.header("X-Request-Id");
        assertNotNull(id);
        assertEquals(id, response.text("/requestId"));
    }

    @Test
    void anIdSentByTheClientIsKept() {
        HttpJson.Response response =
                as("alice", "customer").with("X-Request-Id", "client-chosen-id-1").get("/ledger/v1/accounts/abc");

        assertEquals("client-chosen-id-1", response.header("X-Request-Id"));
        assertEquals("client-chosen-id-1", response.text("/requestId"));
    }

    @Test
    void aTokenThatExpiredOrWasSignedBySomeoneElseIsRefused() {
        String expired = token(KEY, "alice", Instant.now().minusSeconds(600), "customer");
        String forged = token(OTHER_KEY, "alice", Instant.now().plusSeconds(300), "bank-operator");

        assertEquals(401, anonymous.withToken(expired).get("/ledger/v1/accounts/abc").status());
        assertEquals(401, anonymous.withToken(forged).get("/ledger/v1/accounts/abc").status());
        assertEquals(401, anonymous.withToken("not-a-token").get("/ledger/v1/accounts/abc").status());
    }

    @Test
    void aCustomerCannotCallTheInternalLedgerEndpoints() {
        HttpJson customer = as("alice", "customer");

        HttpJson.Response entry = customer.post("/ledger/v1/journal-entries", Map.of("reference", "x"));
        HttpJson.Response hold = customer.post("/ledger/v1/holds", Map.of("reference", "x"));
        HttpJson.Response freeze = customer.post("/ledger/v1/accounts/abc/freeze", null);
        HttpJson.Response metrics = customer.get("/payments/actuator/prometheus");

        assertEquals(403, metrics.status());
        assertEquals(403, entry.status());
        assertEquals("FORBIDDEN", entry.text("/code"));
        assertEquals(403, hold.status());
        assertEquals(403, freeze.status());
        assertTrue(SEEN.isEmpty(), "The service behind the gateway must not be called");
    }

    @Test
    void anOperatorCanCallTheInternalLedgerEndpoints() {
        HttpJson.Response response =
                as("olivia", "customer", "bank-operator").post("/ledger/v1/journal-entries", Map.of("reference", "x"));

        assertEquals(200, response.status());
        assertEquals("/v1/journal-entries", response.text("/path"));
    }

    @Test
    void infoEndpointsNeedNoToken() {
        assertEquals("api-gateway", anonymous.get("/v1/info").text("/service"));
        assertEquals(200, anonymous.get("/ledger/v1/info").status());
        assertEquals(200, anonymous.get("/actuator/health").status());
    }

    @Test
    void aClientThatSendsTooMuchIsToldToSlowDown() {
        HttpJson busy = as("busy-" + UUID.randomUUID(), "customer");
        for (int i = 0; i < 25; i++) {
            assertEquals(200, busy.get("/wealth/v1/portfolios").status());
        }

        HttpJson.Response refused = busy.get("/wealth/v1/portfolios");

        assertEquals(429, refused.status());
        assertEquals("0", refused.header("X-RateLimit-Remaining"));
        // Another client is not affected.
        assertEquals(200, as("calm-" + UUID.randomUUID(), "customer").get("/wealth/v1/portfolios").status());
    }

    @Test
    void whenAServiceIsDownTheClientGetsAClearAnswerQuickly() {
        HttpJson.Response response = as("alice", "customer").get("/payments/v1/payments/abc");

        assertEquals(503, response.status());
        assertEquals("SERVICE_UNAVAILABLE", response.text("/code"));
        assertEquals("5", response.header("Retry-After"));
    }

    @Test
    void aServiceThatIsTooSlowIsGivenUpOn() {
        long start = System.nanoTime();

        HttpJson.Response response = as("alice", "customer").get("/ledger/v1/slow");

        long millis = (System.nanoTime() - start) / 1_000_000;
        assertEquals(503, response.status());
        assertEquals("SERVICE_UNAVAILABLE", response.text("/code"));
        assertTrue(millis < 3500, "The gateway should give up after about a second, took " + millis + " ms");
    }

    private static String token(RSAKey key, String user, Instant expires, String... roles) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(user)
                    .issuer("http://localhost/test")
                    .issueTime(Date.from(expires.minusSeconds(900)))
                    .expirationTime(Date.from(expires))
                    .claim("realm_access", Map.of("roles", List.of(roles)))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static RSAKey newKey() {
        try {
            return new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Answers every call with the path it saw and the request id it was given. /v1/slow takes three seconds. */
    private static HttpServer backend() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().toString();
                SEEN.add(path);
                exchange.getRequestBody().readAllBytes();
                if (path.startsWith("/v1/slow")) {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                String requestId = exchange.getRequestHeaders().getFirst("X-Request-Id");
                byte[] body = ("{\"path\":\"" + path + "\",\"requestId\":\"" + requestId + "\"}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var out = exchange.getResponseBody()) {
                    out.write(body);
                } catch (IOException ignored) {
                    // The gateway gave up on a slow call and closed the connection.
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
