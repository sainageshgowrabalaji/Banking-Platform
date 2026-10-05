package com.sainagesh.bank.payments.adapter.out.ledger;

import com.sainagesh.bank.money.Money;
import com.sainagesh.bank.payments.application.port.Ledger;
import com.sainagesh.bank.payments.config.PaymentsProperties;
import com.sainagesh.bank.payments.domain.ReasonCode;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Calls the ledger service over HTTP.
 *
 * <p>Calling another service is the riskiest thing a service does, because the other side can be slow
 * or down. Three protections are used here.
 *
 * <ul>
 *   <li><b>Timeouts.</b> A call that gets no answer is given up after a few seconds, so a slow ledger
 *       cannot hold up every thread of this service
 *   <li><b>A circuit breaker.</b> When too many calls fail, the breaker opens and further calls fail at
 *       once, without waiting for a timeout. That gives the ledger room to recover. After a pause the
 *       breaker lets a few calls through to test it, and closes again if they work
 *   <li><b>Calls that can be repeated.</b> Each call carries the payment's reference, and the ledger
 *       does a reference only once. So the saga can simply try again later
 * </ul>
 *
 * <p>A refusal by a ledger rule (a 4xx answer with a code) is a normal result, not a failure. Only
 * timeouts, connection errors and 5xx answers count against the breaker.
 */
@Component
class HttpLedger implements Ledger {

    private final RestClient http;
    private final CircuitBreaker breaker;

    HttpLedger(RestClient.Builder builder, PaymentsProperties properties, CircuitBreakerRegistry breakers) {
        PaymentsProperties.LedgerClient settings = properties.ledger();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(settings.connectTimeout()).build());
        factory.setReadTimeout(settings.readTimeout());
        this.http = builder.baseUrl(settings.url()).requestFactory(factory).build();
        this.breaker = breakers.circuitBreaker("ledger");
    }

    /** The parts of a ledger answer this adapter needs. */
    private record Answer(int status, JsonNode body) {

        boolean ok() {
            return status >= 200 && status < 300;
        }

        String code() {
            return body == null || body.get("code") == null ? "" : body.get("code").asString();
        }

        String detail() {
            return body == null || body.get("detail") == null ? null : body.get("detail").asString();
        }
    }

    @Override
    public HoldResult placeHold(UUID accountId, Money amount, String reference) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("accountId", accountId.toString());
        request.put("amount", money(amount));
        request.put("reference", reference);
        Answer answer = post("/v1/holds", reference, request);
        if (answer.ok()) {
            return new HoldResult.Placed(UUID.fromString(answer.body().get("id").asString()));
        }
        if (answer.code().equals("HOLD_NOT_ACTIVE")) {
            // This payment had a hold and it has ended, by a release or by running out of time.
            return new HoldResult.Refused(ReasonCode.MS03, "The money reserved for this payment is no longer reserved");
        }
        return new HoldResult.Refused(reasonFor(answer, "the account to debit"), answer.detail());
    }

    @Override
    public void releaseHold(UUID holdId) {
        Answer answer = post("/v1/holds/" + holdId + "/release", "release-" + holdId, null);
        if (answer.ok()) {
            return;
        }
        // A hold that was already captured, or that the ledger never had, has nothing left to give
        // back. Go by the ledger's own code. A bare 404 or 409 could come from anything in between.
        switch (answer.code()) {
            case "HOLD_NOT_ACTIVE", "HOLD_NOT_FOUND" -> {}
            default -> throw new LedgerUnavailableException(
                    "The ledger did not release hold " + holdId + ". It answered " + answer.status() + " " + answer.code(),
                    null);
        }
    }

    @Override
    public PostResult postTransfer(String reference, UUID debitAccountId, UUID creditAccountId, Money amount, UUID holdId) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("reference", reference);
        request.put("holdId", holdId.toString());
        request.put("postings", List.of(line(debitAccountId, "DEBIT", amount), line(creditAccountId, "CREDIT", amount)));
        Answer answer = post("/v1/journal-entries", reference, request);
        if (answer.ok()) {
            return new PostResult.Posted(UUID.fromString(answer.body().get("id").asString()));
        }
        return new PostResult.Refused(reasonFor(answer, "the account to credit"), answer.detail());
    }

    /** Turns the ledger's error code into the ISO 20022 reason a payment carries. */
    private static ReasonCode reasonFor(Answer answer, String account) {
        return switch (answer.code()) {
            case "INSUFFICIENT_FUNDS" -> ReasonCode.AM04;
            case "ACCOUNT_NOT_FOUND" -> ReasonCode.AC01;
            case "ACCOUNT_CLOSED", "ACCOUNT_NOT_ACTIVE" -> ReasonCode.AC04;
            case "ACCOUNT_FROZEN" -> ReasonCode.AC06;
            case "CURRENCY_MISMATCH" -> ReasonCode.AC01;
            // The account was busy. The ledger asks for the same request again.
            case "CONCURRENT_UPDATE" -> throw new LedgerUnavailableException("The ledger was busy with " + account, null);
            default -> {
                if (answer.status() == 408 || answer.status() == 429) {
                    throw new LedgerUnavailableException("The ledger answered " + answer.status(), null);
                }
                throw new UnexpectedRefusalException(
                        "The ledger answered " + answer.status() + " " + answer.code() + " for " + account + ". "
                                + answer.detail());
            }
        };
    }

    private Answer post(String path, String idempotencyKey, Object body) {
        Supplier<Answer> call = () -> {
            RestClient.RequestBodySpec request = http.post()
                    .uri(path)
                    .header("Idempotency-Key", idempotencyKey)
                    .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_PROBLEM_JSON);
            if (body != null) {
                request.contentType(MediaType.APPLICATION_JSON).body(body);
            }
            Answer answer = request.exchange((req, response) -> {
                int status = response.getStatusCode().value();
                JsonNode json = null;
                try {
                    json = response.bodyTo(JsonNode.class);
                } catch (RuntimeException unreadable) {
                    // An answer with no JSON body. The status alone decides.
                }
                return new Answer(status, json);
            });
            if (answer.status() >= 500) {
                throw new LedgerUnavailableException("The ledger answered " + answer.status(), null);
            }
            return answer;
        };
        try {
            return breaker.executeSupplier(call);
        } catch (LedgerUnavailableException e) {
            throw e;
        } catch (CallNotPermittedException e) {
            throw new LedgerUnavailableException("The ledger is failing. Calls to it are paused for a moment", e);
        } catch (RuntimeException e) {
            throw new LedgerUnavailableException("The ledger did not answer", e);
        }
    }

    private static Map<String, Object> money(Money amount) {
        return Map.of("amount", amount.amount().toPlainString(), "currency", amount.currency().getCurrencyCode());
    }

    private static Map<String, Object> line(UUID accountId, String side, Money amount) {
        return Map.of("accountId", accountId.toString(), "side", side, "amount", money(amount));
    }
}
