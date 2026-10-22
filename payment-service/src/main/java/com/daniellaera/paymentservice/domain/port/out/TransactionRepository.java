package com.daniellaera.paymentservice.domain.port.out;

import com.daniellaera.paymentservice.domain.model.PaymentIntentAlreadyUsedException;
import com.daniellaera.paymentservice.domain.model.PaymentTransaction;
import com.daniellaera.paymentservice.domain.model.TransactionAlreadyRecordedException;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository {
    /**
     * Inserts a new transaction.
     *
     * @throws TransactionAlreadyRecordedException when the order already has one (unique per order)
     * @throws PaymentIntentAlreadyUsedException when its paying intent already paid for another order
     */
    PaymentTransaction save(PaymentTransaction transaction);

    Optional<PaymentTransaction> findByOrderId(long orderId);

    Optional<PaymentTransaction> findByPaymentIntentId(String paymentIntentId);

    List<PaymentTransaction> findAll();

    Optional<PaymentTransaction> findById(long id);
}
