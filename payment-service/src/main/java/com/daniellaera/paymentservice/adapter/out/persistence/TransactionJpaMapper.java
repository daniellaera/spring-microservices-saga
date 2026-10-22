package com.daniellaera.paymentservice.adapter.out.persistence;

import com.daniellaera.paymentservice.domain.model.PaymentTransaction;

public final class TransactionJpaMapper {

    private TransactionJpaMapper() {
    }

    public static PaymentTransaction toDomain(TransactionJpaEntity entity) {
        return new PaymentTransaction(entity.getId(), entity.getOrderId(), entity.getTotalAmount(), entity.getStatus(),
                entity.getPaymentIntentId(), entity.getCreatedAt());
    }

    /** Maps a transaction that has never been persisted; id and createdAt are assigned on insert. */
    public static TransactionJpaEntity toNewEntity(PaymentTransaction transaction) {
        TransactionJpaEntity entity = new TransactionJpaEntity();
        entity.setOrderId(transaction.orderId());
        entity.setTotalAmount(transaction.totalAmount());
        entity.setStatus(transaction.status());
        entity.setPaymentIntentId(transaction.paymentIntentId());
        return entity;
    }
}
