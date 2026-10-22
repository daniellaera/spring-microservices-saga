package com.daniellaera.paymentservice.application;

import com.daniellaera.paymentservice.domain.model.PaymentTransaction;
import com.daniellaera.paymentservice.domain.model.TransactionNotFoundException;
import com.daniellaera.paymentservice.domain.port.in.TransactionQuery;
import com.daniellaera.paymentservice.domain.port.out.TransactionRepository;

import java.util.List;

public class TransactionQueryService implements TransactionQuery {

    private final TransactionRepository transactionRepository;

    public TransactionQueryService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Override
    public List<PaymentTransaction> getAllTransactions() {
        return transactionRepository.findAll();
    }

    @Override
    public PaymentTransaction getTransactionById(long id) {
        return transactionRepository.findById(id).orElseThrow(() -> new TransactionNotFoundException(id));
    }
}
