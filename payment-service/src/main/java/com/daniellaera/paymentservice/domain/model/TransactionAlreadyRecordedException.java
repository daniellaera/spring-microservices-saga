package com.daniellaera.paymentservice.domain.model;

/** The order already has a payment transaction: there is at most one per order. */
public class TransactionAlreadyRecordedException extends RuntimeException {

    public TransactionAlreadyRecordedException(long orderId, Throwable cause) {
        super("A transaction is already recorded for order " + orderId, cause);
    }
}
