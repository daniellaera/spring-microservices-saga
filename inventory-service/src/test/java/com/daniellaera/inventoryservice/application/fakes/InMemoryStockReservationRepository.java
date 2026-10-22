package com.daniellaera.inventoryservice.application.fakes;

import com.daniellaera.inventoryservice.domain.model.StockReservation;
import com.daniellaera.inventoryservice.domain.port.out.StockReservationRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Enforces the (orderId, productName) unique constraint, and can be told to fail the n-th insert. */
public class InMemoryStockReservationRepository implements StockReservationRepository {

    private final List<StockReservation> reservations = new ArrayList<>();
    private int failOnInsert = -1;
    private int inserts;

    public void given(Long orderId, String productName, int quantity) {
        reservations.add(new StockReservation(UUID.randomUUID(), orderId, productName, quantity,
                LocalDateTime.of(2025, 1, 1, 9, 0)));
    }

    /** The n-th call to save (1-based) throws, like a failed insert. */
    public void failOnInsert(int n) {
        failOnInsert = n;
    }

    @Override
    public StockReservation save(StockReservation reservation) {
        if (++inserts == failOnInsert) {
            throw new IllegalStateException("simulated reservation insert failure");
        }
        boolean duplicate = reservations.stream().anyMatch(r -> r.orderId().equals(reservation.orderId())
                && r.productName().equals(reservation.productName()));
        if (duplicate) {
            throw new IllegalStateException("duplicate reservation " + reservation.orderId() + "/"
                    + reservation.productName());
        }
        StockReservation stored = new StockReservation(UUID.randomUUID(), reservation.orderId(),
                reservation.productName(), reservation.quantity(), LocalDateTime.now());
        reservations.add(stored);
        return stored;
    }

    @Override
    public List<StockReservation> findByOrderId(long orderId) {
        return reservations.stream().filter(r -> r.orderId() == orderId).toList();
    }

    @Override
    public boolean existsByOrderId(long orderId) {
        return reservations.stream().anyMatch(r -> r.orderId() == orderId);
    }

    public List<StockReservation> all() {
        return List.copyOf(reservations);
    }
}
