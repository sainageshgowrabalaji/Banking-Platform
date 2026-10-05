package com.sainagesh.bank.testing;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Gives integration tests a real PostgreSQL and a real Kafka.
 *
 * <p>There are two ways to get them.
 *
 * <ul>
 *   <li><b>Containers.</b> The default. With Docker running, one PostgreSQL and one Kafka container are
 *       started for the whole test run and shared by every test class
 *   <li><b>Outside servers.</b> Set {@code BANK_TEST_POSTGRES_URL} (and optionally
 *       {@code BANK_TEST_POSTGRES_USER} and {@code BANK_TEST_POSTGRES_PASSWORD}) and
 *       {@code BANK_TEST_KAFKA} to servers that are already running. Useful where Docker is not allowed
 * </ul>
 *
 * <p>Either way, every Spring test context gets its own empty database, so tests never see each
 * other's rows.
 */
public final class TestInfrastructure {

    private static final Logger log = LoggerFactory.getLogger(TestInfrastructure.class);

    private static final String POSTGRES_URL = setting("BANK_TEST_POSTGRES_URL");
    private static final String POSTGRES_USER = setting("BANK_TEST_POSTGRES_USER");
    private static final String POSTGRES_PASSWORD = setting("BANK_TEST_POSTGRES_PASSWORD");
    private static final String KAFKA = setting("BANK_TEST_KAFKA");

    private static PostgreSQLContainer postgres;
    private static KafkaContainer kafka;
    private static Boolean docker;

    private TestInfrastructure() {}

    private static String setting(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            value = System.getenv(name);
        }
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean outside() {
        return POSTGRES_URL != null && KAFKA != null;
    }

    private static synchronized boolean dockerRunning() {
        if (docker == null) {
            try {
                docker = DockerClientFactory.instance().isDockerAvailable();
            } catch (RuntimeException e) {
                docker = false;
            }
        }
        return docker;
    }

    /** True when the tests have something to run against. */
    public static boolean available() {
        return outside() || dockerRunning();
    }

    static String describe() {
        return outside() ? "PostgreSQL and Kafka from outside servers" : "PostgreSQL and Kafka in containers";
    }

    /**
     * Points the service under test at a fresh, empty database.
     *
     * @param service the name of the service, used as the start of the database name
     */
    public static synchronized void postgres(DynamicPropertyRegistry registry, String service) {
        String adminUrl;
        String user;
        String password;
        if (outside()) {
            adminUrl = POSTGRES_URL;
            user = POSTGRES_USER != null ? POSTGRES_USER : "postgres";
            password = POSTGRES_PASSWORD != null ? POSTGRES_PASSWORD : "";
        } else {
            if (postgres == null) {
                postgres = new PostgreSQLContainer("postgres:17");
                postgres.start();
            }
            adminUrl = postgres.getJdbcUrl();
            user = postgres.getUsername();
            password = postgres.getPassword();
        }
        String database = service.replace('-', '_') + "_" + UUID.randomUUID().toString().substring(0, 8);
        try (Connection connection = DriverManager.getConnection(adminUrl, user, password);
                Statement statement = connection.createStatement()) {
            statement.execute("create database " + database);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create the test database " + database, e);
        }
        String url = adminUrl.replaceFirst("(//[^/]+/)[^?]*", "$1" + database);
        log.info("Test database {}", url);
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> user);
        registry.add("spring.datasource.password", () -> password);
    }

    /** Points the service under test at Kafka. */
    public static synchronized void kafka(DynamicPropertyRegistry registry) {
        String servers;
        if (outside()) {
            servers = KAFKA;
        } else {
            if (kafka == null) {
                kafka = new KafkaContainer("apache/kafka:4.1.0");
                kafka.start();
            }
            servers = kafka.getBootstrapServers();
        }
        registry.add("spring.kafka.bootstrap-servers", () -> servers);
    }

    /** The Kafka address, for a test that wants its own consumer. Call it after {@link #kafka}. */
    public static synchronized String kafkaBootstrapServers() {
        return outside() ? KAFKA : kafka.getBootstrapServers();
    }
}
