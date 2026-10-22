package com.daniellaera.paymentservice.application.fakes;

import com.daniellaera.paymentservice.domain.model.PaymentTransaction;
import com.daniellaera.paymentservice.domain.port.out.TransactionRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Behaves like the persistence adapter: inserts only, assigning id and createdAt; no uniqueness per order. */
public class InMemoryTransactionRepository implements TransactionRepository {

    private final List<PaymentTransaction> transactions = new ArrayList<>();
    private final EffectLog effectLog;
    private long nextId = 1;
    private LocalDateTime clock = LocalDateTime.of(2025, 1, 1, 9, 0);
    private RuntimeException failure;

    public InMemoryTransactionRepository(EffectLog effectLog) {
        this.effectLog = effectLog;
    }

    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    @Override
    public PaymentTransaction save(PaymentTransaction transaction) {
        if (failure != null) {
            throw failure;
        }
        clock = clock.plusMinutes(1);
        PaymentTransaction stored = new PaymentTransaction(nextId++, transaction.orderId(), transaction.totalAmount(),
                transaction.status(), clock);
        transactions.add(stored);
        effectLog.add("save " + stored.status());
        return stored;
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
