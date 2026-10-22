package com.daniellaera.paymentservice.application.fakes;

import com.daniellaera.paymentservice.domain.model.PaymentIntentAlreadyUsedException;
import com.daniellaera.paymentservice.domain.model.PaymentTransaction;
import com.daniellaera.paymentservice.domain.model.TransactionAlreadyRecordedException;
import com.daniellaera.paymentservice.domain.port.out.TransactionRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Behaves like the persistence adapter: inserts only, assigning id and createdAt, at most one per order and one per
 * paying intent.
 */
public class InMemoryTransactionRepository implements TransactionRepository {

    private final List<PaymentTransaction> transactions = new ArrayList<>();
    private final EffectLog effectLog;
    private long nextId = 1;
    private LocalDateTime clock = LocalDateTime.of(2025, 1, 1, 9, 0);
    private RuntimeException failure;
    private PaymentTransaction concurrentInsert;

    public InMemoryTransactionRepository(EffectLog effectLog) {
        this.effectLog = effectLog;
    }

    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    /** Simulates another delivery inserting this transaction between the caller's lookup and its save. */
    public void insertConcurrentlyBeforeNextSave(PaymentTransaction transaction) {
        this.concurrentInsert = transaction;
    }

    @Override
    public PaymentTransaction save(PaymentTransaction transaction) {
        if (failure != null) {
            throw failure;
        }
        if (concurrentInsert != null) {
            PaymentTransaction concurrent = concurrentInsert;
            concurrentInsert = null;
            insert(concurrent);
        }
        if (findByOrderId(transaction.orderId()).isPresent()) {
            throw new TransactionAlreadyRecordedException(transaction.orderId(), null);
        }
        if (transaction.paymentIntentId() != null && findByPaymentIntentId(transaction.paymentIntentId()).isPresent()) {
            throw new PaymentIntentAlreadyUsedException(transaction.paymentIntentId(), null);
        }
        PaymentTransaction stored = insert(transaction);
        effectLog.add("save " + stored.status());
        return stored;
    }

    private PaymentTransaction insert(PaymentTransaction transaction) {
        clock = clock.plusMinutes(1);
        PaymentTransaction stored = new PaymentTransaction(nextId++, transaction.orderId(), transaction.totalAmount(),
                transaction.status(), transaction.paymentIntentId(), clock);
        transactions.add(stored);
        return stored;
    }

    @Override
    public Optional<PaymentTransaction> findByOrderId(long orderId) {
        return transactions.stream().filter(t -> t.orderId() == orderId).findFirst();
    }

    @Override
    public Optional<PaymentTransaction> findByPaymentIntentId(String paymentIntentId) {
        return transactions.stream().filter(t -> paymentIntentId.equals(t.paymentIntentId())).findFirst();
    }

    @Override
    public List<PaymentTransaction> findAll() {
        return List.copyOf(transactions);
    }

    @Override
    public Optional<PaymentTransaction> findById(long id) {
        return transactions.stream().filter(t -> t.id() == id).findFirst();
    }
}
