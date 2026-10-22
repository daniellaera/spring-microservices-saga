package com.daniellaera.paymentservice.adapter.out.messaging;

import com.daniellaera.paymentservice.domain.model.PaymentStatus;
import com.daniellaera.paymentservice.domain.model.PaymentTransaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * PAYMENT_SUCCEEDED / PAYMENT_FAILED audit payload. It used to be the JPA entity serialized as-is, which Jackson
 * writes with its properties in alphabetical order; the components are declared in that order to keep the JSON
 * identical.
 */
record TransactionAuditPayload(LocalDateTime createdAt, Long id, Long orderId, PaymentStatus status,
                               BigDecimal totalAmount) {

    static TransactionAuditPayload of(PaymentTransaction transaction) {
        return new TransactionAuditPayload(transaction.createdAt(), transaction.id(), transaction.orderId(),
                transaction.status(), transaction.totalAmount());
    }
}
