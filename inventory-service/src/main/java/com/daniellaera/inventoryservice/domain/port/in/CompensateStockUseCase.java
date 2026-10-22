package com.daniellaera.inventoryservice.domain.port.in;

public interface CompensateStockUseCase {
    void compensate(PaymentOutcome outcome);
}
