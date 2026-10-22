package com.daniellaera.paymentservice.adapter.out.persistence;

import com.daniellaera.paymentservice.domain.model.PaymentIntentAlreadyUsedException;
import com.daniellaera.paymentservice.domain.model.PaymentTransaction;
import com.daniellaera.paymentservice.domain.model.TransactionAlreadyRecordedException;
import com.daniellaera.paymentservice.domain.port.out.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Transactions are only ever inserted, at most one per order (uk_transaction_order_id) and one per paying intent
 * (uk_transaction_payment_intent_id). No transaction of its own: Spring Data's per-call one, so a duplicate insert
 * fails right here and is reported as the domain exception of whichever rule it broke.
 */
@Component
@RequiredArgsConstructor
public class TransactionPersistenceAdapter implements TransactionRepository {

    private final TransactionJpaRepository transactionJpaRepository;

    @Override
    public PaymentTransaction save(PaymentTransaction transaction) {
        try {
            return TransactionJpaMapper.toDomain(
                    transactionJpaRepository.saveAndFlush(TransactionJpaMapper.toNewEntity(transaction)));
        } catch (DataIntegrityViolationException e) {
            if (transactionJpaRepository.findByOrderId(transaction.orderId()).isPresent()) {
                throw new TransactionAlreadyRecordedException(transaction.orderId(), e);
            }
            if (transaction.paymentIntentId() != null
                    && transactionJpaRepository.findByPaymentIntentId(transaction.paymentIntentId()).isPresent()) {
                throw new PaymentIntentAlreadyUsedException(transaction.paymentIntentId(), e);
            }
            throw e;
        }
    }

    @Override
    public Optional<PaymentTransaction> findByOrderId(long orderId) {
        return transactionJpaRepository.findByOrderId(orderId).map(TransactionJpaMapper::toDomain);
    }

    @Override
    public Optional<PaymentTransaction> findByPaymentIntentId(String paymentIntentId) {
        return transactionJpaRepository.findByPaymentIntentId(paymentIntentId).map(TransactionJpaMapper::toDomain);
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
