package com.sainagesh.bank.payments;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A small ledger for tests. It answers the three calls the payments service makes, over real HTTP,
 * and follows the same rules as the real ledger. It can also be switched "down" to see how the
 * payments service copes.
 *
 * <p>The two real services are run together by scripts/demo.sh.
 */
final class FakeLedger {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    static final class Account {
        BigDecimal balance = BigDecimal.ZERO;
        BigDecimal held = BigDecimal.ZERO;
        String status = "ACTIVE";
    }

    record Hold(String id, String accountId, BigDecimal amount, String reference, String[] status) {}

    private final HttpServer server;
    private final Map<String, Account> accounts = new ConcurrentHashMap<>();
    private final Map<String, Hold> holdsById = new ConcurrentHashMap<>();
    private final Map<String, Hold> holdsByReference = new ConcurrentHashMap<>();
    private final Map<String, String> entriesByReference = new ConcurrentHashMap<>();
    private final List<String> calls = new ArrayList<>();
    private final Set<String> postsRefusedFrom = ConcurrentHashMap.newKeySet();
    private volatile boolean down;
    private volatile boolean loseNextHoldAnswer;

    FakeLedger() {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/", this::handle);
        server.start();
    }

    String url() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    /** Opens an account with money in it and returns its id. */
    String account(String balance) {
        String id = UUID.randomUUID().toString();
        Account account = new Account();
        account.balance = new BigDecimal(balance);
        accounts.put(id, account);
        return id;
    }

    /** Adds one of the bank's own accounts, which may go below zero. */
    void internalAccount(String id) {
        accounts.put(id, new Account());
    }

    Account get(String id) {
        return accounts.get(id);
    }

    String balance(String id) {
        return accounts.get(id).balance.toPlainString();
    }

    String available(String id) {
        Account account = accounts.get(id);
        return account.balance.subtract(account.held).toPlainString();
    }

    /**
     * The next hold is placed, but its answer never reaches the caller. This is what a timeout looks
     * like from the other side. The work was done and nobody was told.
     */
    void loseNextHoldAnswer() {
        loseNextHoldAnswer = true;
    }

    /** From now on an entry that debits this account is refused, as if the account had just been frozen. */
    void refusePostsFrom(String accountId, boolean refuse) {
        if (refuse) {
            postsRefusedFrom.add(accountId);
        } else {
            postsRefusedFrom.remove(accountId);
        }
    }

    void down(boolean value) {
        down = value;
    }

    synchronized List<String> calls() {
        return List.copyOf(calls);
    }

    synchronized long callsTo(String path) {
        return calls.stream().filter(call -> call.equals(path)).count();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        synchronized (this) {
            calls.add(path);
        }
        try {
            if (down) {
                respond(exchange, 503, problem("UNAVAILABLE", "The ledger is down"));
                return;
            }
            byte[] bytes = exchange.getRequestBody().readAllBytes();
            JsonNode body = bytes.length == 0 ? JSON.createObjectNode() : JSON.readTree(bytes);
            synchronized (this) {
                if (path.equals("/v1/holds")) {
                    placeHold(exchange, body);
                } else if (path.startsWith("/v1/holds/") && path.endsWith("/release")) {
                    releaseHold(exchange, path.split("/")[3]);
                } else if (path.equals("/v1/journal-entries")) {
                    post(exchange, body);
                } else {
                    respond(exchange, 404, problem("NOT_FOUND", path));
                }
            }
        } catch (RuntimeException e) {
            respond(exchange, 500, problem("INTERNAL_ERROR", e.toString()));
        }
    }

    private void placeHold(HttpExchange exchange, JsonNode body) throws IOException {
        String reference = body.get("reference").asString();
        Hold before = holdsByReference.get(reference);
        if (before != null) {
            if (!before.status()[0].equals("ACTIVE")) {
                respond(exchange, 409, problem("HOLD_NOT_ACTIVE", "The hold is " + before.status()[0]));
                return;
            }
            respond(exchange, 200, hold(before));
            return;
        }
        String accountId = body.get("accountId").asString();
        BigDecimal amount = new BigDecimal(body.get("amount").get("amount").asString());
        Account account = accounts.get(accountId);
        if (account == null) {
            respond(exchange, 404, problem("ACCOUNT_NOT_FOUND", "There is no account " + accountId));
            return;
        }
        if (!account.status.equals("ACTIVE")) {
            respond(exchange, 422, problem("ACCOUNT_" + account.status, "The account is " + account.status));
            return;
        }
        if (account.balance.subtract(account.held).compareTo(amount) < 0) {
            respond(exchange, 422, problem("INSUFFICIENT_FUNDS", "Not enough money"));
            return;
        }
        account.held = account.held.add(amount);
        Hold hold = new Hold(UUID.randomUUID().toString(), accountId, amount, reference, new String[] {"ACTIVE"});
        holdsById.put(hold.id(), hold);
        holdsByReference.put(reference, hold);
        if (loseNextHoldAnswer) {
            loseNextHoldAnswer = false;
            respond(exchange, 503, problem("UNAVAILABLE", "The answer was lost on the way"));
            return;
        }
        respond(exchange, 201, hold(hold));
    }

    private void releaseHold(HttpExchange exchange, String holdId) throws IOException {
        Hold hold = holdsById.get(holdId);
        if (hold == null) {
            respond(exchange, 404, problem("HOLD_NOT_FOUND", holdId));
            return;
        }
        if (hold.status()[0].equals("ACTIVE")) {
            Account account = accounts.get(hold.accountId());
            account.held = account.held.subtract(hold.amount());
            hold.status()[0] = "RELEASED";
        }
        respond(exchange, 200, hold(hold));
    }

    private void post(HttpExchange exchange, JsonNode body) throws IOException {
        String reference = body.get("reference").asString();
        String before = entriesByReference.get(reference);
        if (before != null) {
            respond(exchange, 200, Map.of("id", before, "reference", reference));
            return;
        }
        JsonNode debit = body.get("postings").get(0);
        JsonNode credit = body.get("postings").get(1);
        BigDecimal amount = new BigDecimal(debit.get("amount").get("amount").asString());
        Account from = accounts.get(debit.get("accountId").asString());
        Account to = accounts.get(credit.get("accountId").asString());
        if (to == null) {
            respond(exchange, 404, problem("ACCOUNT_NOT_FOUND", "There is no account " + credit.get("accountId").asString()));
            return;
        }
        if (to.status.equals("CLOSED")) {
            respond(exchange, 422, problem("ACCOUNT_CLOSED", "The account is closed"));
            return;
        }
        if (postsRefusedFrom.contains(debit.get("accountId").asString())) {
            respond(exchange, 422, problem("ACCOUNT_FROZEN", "The account is frozen"));
            return;
        }
        Hold hold = holdsById.get(body.get("holdId").asString());
        if (hold != null && hold.status()[0].equals("ACTIVE")) {
            from.held = from.held.subtract(hold.amount());
            hold.status()[0] = "CAPTURED";
        }
        from.balance = from.balance.subtract(amount);
        to.balance = to.balance.add(amount);
        String id = UUID.randomUUID().toString();
        entriesByReference.put(reference, id);
        respond(exchange, 201, Map.of("id", id, "reference", reference));
    }

    private static Map<String, Object> hold(Hold hold) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", hold.id());
        json.put("accountId", hold.accountId());
        json.put("reference", hold.reference());
        json.put("status", hold.status()[0]);
        return json;
    }

    private static Map<String, Object> problem(String code, String detail) {
        return Map.of("code", code, "detail", detail, "title", code);
    }

    private static void respond(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = JSON.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", status >= 400 ? "application/problem+json" : "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
