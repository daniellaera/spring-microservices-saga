package com.daniellaera.paymentservice.adapter.out.persistence;

import com.daniellaera.paymentservice.domain.model.PaymentTransaction;
import com.daniellaera.paymentservice.domain.port.out.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Transactions are only ever inserted. No transaction of its own: Spring Data's per-call one, as before. */
@Component
@RequiredArgsConstructor
public class TransactionPersistenceAdapter implements TransactionRepository {

    private final TransactionJpaRepository transactionJpaRepository;

    @Override
    public PaymentTransaction save(PaymentTransaction transaction) {
        return TransactionJpaMapper.toDomain(transactionJpaRepository.save(TransactionJpaMapper.toNewEntity(transaction)));
    }

    @Override
    public List<PaymentTransaction> findAll() {
        return transactionJpaRepository.findAll().stream().map(TransactionJpaMapper::toDomain).toList();
    }

    @Override
    public Optional<PaymentTransaction> findById(long id) {
        return transactionJpaRepository.findById(id).map(TransactionJpaMapper::toDomain);
    }
}
