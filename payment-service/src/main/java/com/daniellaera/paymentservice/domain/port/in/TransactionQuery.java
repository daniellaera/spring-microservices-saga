package com.daniellaera.paymentservice.domain.port.in;

import com.daniellaera.paymentservice.domain.model.PaymentTransaction;

import java.util.List;

public interface TransactionQuery {
    List<PaymentTransaction> getAllTransactions();

    PaymentTransaction getTransactionById(long id);
}
