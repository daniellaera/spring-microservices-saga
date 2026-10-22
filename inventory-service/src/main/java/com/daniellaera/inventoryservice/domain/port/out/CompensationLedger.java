package com.daniellaera.inventoryservice.domain.port.out;

/** One entry per compensated order; its presence makes compensation skip a redelivered payment result. */
public interface CompensationLedger {
    boolean isCompensated(long orderId);

    void record(long orderId, String productName, int quantity);
}
