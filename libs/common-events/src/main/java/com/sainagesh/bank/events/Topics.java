package com.sainagesh.bank.events;

/** Kafka topic names. One place, so producers and consumers can never disagree on a name. */
public final class Topics {

    public static final String ACCOUNTS = "bank.accounts.v1";
    public static final String LEDGER = "bank.ledger.v1";
    public static final String PAYMENTS = "bank.payments.v1";
    public static final String COMPLIANCE = "bank.compliance.v1";
    public static final String TICKS = "markets.ticks.v1";
    public static final String ORDERS = "markets.orders.v1";
    public static final String EXECUTIONS = "markets.executions.v1";
    public static final String POSITIONS = "markets.positions.v1";
    public static final String SETTLEMENTS = "posttrade.settlements.v1";
    public static final String PORTFOLIOS = "wealth.portfolios.v1";

    private Topics() {}
}
