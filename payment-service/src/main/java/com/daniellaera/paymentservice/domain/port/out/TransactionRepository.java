package com.daniellaera.paymentservice.domain.port.out;

import com.daniellaera.paymentservice.domain.model.PaymentTransaction;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository {
    /** Inserts a new transaction; there is no uniqueness per order. */
    PaymentTransaction save(PaymentTransaction transaction);

    List<PaymentTransaction> findAll();

    Optional<PaymentTransaction> findById(long id);
}
