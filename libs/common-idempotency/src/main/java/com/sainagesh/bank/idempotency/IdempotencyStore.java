package com.sainagesh.bank.idempotency;

import com.sainagesh.bank.web.ApiException;
import com.sainagesh.bank.web.IdempotencyKey;
import java.time.Duration;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The store behind the {@code Idempotency-Key} header, in the {@code idempotency_record} table.
 *
 * <p>How it is used, inside one database transaction.
 *
 * <ol>
 *   <li>{@link #claim} tries to insert the key. If the insert works, this is the first request and the
 *       caller does the work
 *   <li>The caller calls {@link #complete} with the id of what it created
 *   <li>The transaction commits. The key, the result and the business change are saved together
 * </ol>
 *
 * <p>If a second request with the same key arrives while the first is still running, its insert waits
 * on the unique index until the first one commits. It then finds the stored result and returns it. So
 * two taps on Send create one payment, even when they arrive at the same millisecond.
 *
 * <p>If the first request fails, its transaction rolls back and the key is free again, so the client
 * can retry.
 */
public class IdempotencyStore {

    private final JdbcClient jdbc;

    public IdempotencyStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Claims the key for this request.
     *
     * @param scope       the operation, such as {@code accounts.open}. The same key may be used for two
     *                    different operations
     * @param requestHash the fingerprint of the request body, from {@link RequestHash}
     * @return empty when this is the first request with the key. Otherwise the result of the first one
     * @throws ApiException 422 when the key was used before with a different request body
     */
    public Optional<StoredResult> claim(String scope, IdempotencyKey key, String requestHash) {
        requireTransaction();
        record Existing(String hash, String resourceId, Integer status) {}
        Existing existing = null;
        // Two tries. Between the insert finding a row and the select reading it, housekeeping may
        // remove that row because it was old. Then the key is free again and the insert works.
        for (int attempt = 1; attempt <= 2 && existing == null; attempt++) {
            int inserted = jdbc.sql("""
                    insert into idempotency_record (scope, idem_key, request_hash, created_at)
                    values (:scope, :key, :hash, now())
                    on conflict (scope, idem_key) do nothing
                    """)
                    .param("scope", scope)
                    .param("key", key.value())
                    .param("hash", requestHash)
                    .update();
            if (inserted == 1) {
                return Optional.empty();
            }
            existing = jdbc.sql("""
                    select request_hash, resource_id, response_status
                    from idempotency_record
                    where scope = :scope and idem_key = :key
                    """)
                    .param("scope", scope)
                    .param("key", key.value())
                    .query((rs, n) -> new Existing(
                            rs.getString("request_hash"),
                            rs.getString("resource_id"),
                            (Integer) rs.getObject("response_status")))
                    .optional()
                    .orElse(null);
        }
        if (existing == null) {
            throw ApiException.conflict(
                    "REQUEST_IN_PROGRESS", "This Idempotency-Key is in use by another request. Try again.");
        }
        if (!existing.hash().equals(requestHash)) {
            throw ApiException.rejected(
                    "IDEMPOTENCY_KEY_REUSED",
                    "This Idempotency-Key was already used with a different request. Use a new key for a new request.");
        }
        if (existing.resourceId() == null) {
            throw ApiException.conflict(
                    "REQUEST_IN_PROGRESS", "The first request with this Idempotency-Key has not finished yet. Try again.");
        }
        return Optional.of(new StoredResult(existing.resourceId(), existing.status()));
    }

    /** Stores what the request produced. Call it before the transaction commits. */
    public void complete(String scope, IdempotencyKey key, String resourceId, int status) {
        requireTransaction();
        jdbc.sql("""
                update idempotency_record
                set resource_id = :resourceId, response_status = :status
                where scope = :scope and idem_key = :key
                """)
                .param("resourceId", resourceId)
                .param("status", status)
                .param("scope", scope)
                .param("key", key.value())
                .update();
    }

    /** Removes old records. A key only needs to be remembered for as long as a client might retry. */
    public int purgeOlderThan(Duration age) {
        return jdbc.sql("delete from idempotency_record where created_at < now() - make_interval(secs => :seconds)")
                .param("seconds", age.toSeconds())
                .update();
    }

    private static void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("The idempotency key must be claimed inside the transaction that does the work");
        }
    }
}
