package com.sainagesh.bank.testing;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A very small HTTP client for tests. It calls a running service the way a real client would, over a
 * real socket, and gives back the status and the JSON body.
 */
public final class HttpJson {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String baseUrl;
    private final Map<String, String> headers;
    private final Contract contract;

    public HttpJson(String baseUrl) {
        this(baseUrl, Map.of(), null);
    }

    private HttpJson(String baseUrl, Map<String, String> headers, Contract contract) {
        this.baseUrl = baseUrl;
        this.headers = headers;
        this.contract = contract;
    }

    /** The same client, sending one more header on every call. */
    public HttpJson with(String header, String value) {
        Map<String, String> all = new LinkedHashMap<>(headers);
        all.put(header, value);
        return new HttpJson(baseUrl, all, contract);
    }

    /** The same client, failing the test whenever an exchange does not match the contract. */
    public HttpJson checkedAgainst(Contract contract) {
        return new HttpJson(baseUrl, headers, contract);
    }

    /** The same client, sending a bearer token on every call. */
    public HttpJson withToken(String token) {
        return with("Authorization", "Bearer " + token);
    }

    /** The status, the JSON body (an empty object when there was none) and the raw response. */
    public record Response(int status, JsonNode body, HttpResponse<String> raw) {

        /** The text at a JSON pointer such as {@code /ledger/amount}. Empty when it is missing. */
        public String text(String pointer) {
            JsonNode node = body.at(pointer);
            return node.isMissingNode() || node.isNull() ? "" : node.asString();
        }

        public String header(String name) {
            return raw.headers().firstValue(name).orElse(null);
        }
    }

    public Response get(String path) {
        return send("GET", null, HttpRequest.newBuilder(URI.create(baseUrl + path)).GET());
    }

    /** POST with a fresh Idempotency-Key. */
    public Response post(String path, Object body) {
        return post(path, body, UUID.randomUUID().toString());
    }

    /** POST with the given Idempotency-Key. Pass null to send no key at all. */
    public Response post(String path, Object body, String idempotencyKey) {
        String json = body == null ? "" : body instanceof String text ? text : MAPPER.writeValueAsString(body);
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return send("POST", json, request);
    }

    private Response send(String method, String requestBody, HttpRequest.Builder builder) {
        headers.forEach(builder::header);
        try {
            HttpRequest request = builder.timeout(Duration.ofSeconds(30)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String text = response.body();
            if (contract != null) {
                Map<String, String> sent = new LinkedHashMap<>();
                request.headers().map().forEach((name, values) -> sent.put(name, values.get(0)));
                contract.check(
                        method,
                        request.uri(),
                        sent,
                        requestBody,
                        response.statusCode(),
                        response.headers().firstValue("Content-Type").orElse(null),
                        text);
            }
            JsonNode body = text == null || text.isBlank() || !looksLikeJson(text)
                    ? MAPPER.createObjectNode()
                    : MAPPER.readTree(text);
            return new Response(response.statusCode(), body, response);
        } catch (IOException e) {
            throw new IllegalStateException("The call failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("The call was interrupted", e);
        }
    }

    private static boolean looksLikeJson(String text) {
        String trimmed = text.stripLeading();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }
}
