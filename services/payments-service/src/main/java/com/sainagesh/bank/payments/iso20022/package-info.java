/**
 * ISO 20022 payment messages, the XML format banks and payment networks use with each other.
 *
 * <p>Only the fields this platform needs are read and written. Three messages are covered.
 *
 * <ul>
 *   <li>pain.001, a customer asks its bank to send a payment
 *   <li>pacs.008, one bank sends a customer payment to another bank
 *   <li>pacs.002, the receiving side reports what became of it
 * </ul>
 *
 * <p>This package is plain Java with the JDK's own XML tools. It has no Spring in it.
 */
package com.sainagesh.bank.payments.iso20022;
