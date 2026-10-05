package com.sainagesh.bank.payments.domain;

/**
 * The road a payment travels on.
 *
 * <ul>
 *   <li>BOOK. Both accounts are in this bank. Nothing leaves the building, so it settles at once
 *   <li>ACH. The batch network. Payments are collected, sent together at a cut-off time and settle on a
 *       later business day. Cheap and slow
 *   <li>INSTANT. The real-time network (like FedNow or RTP). One payment at a time, an answer in
 *       seconds, at any hour of any day, and no way to take it back once it is accepted
 * </ul>
 */
public enum Rail {
    BOOK,
    ACH,
    INSTANT
}
