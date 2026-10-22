package com.daniellaera.inventoryservice.adapter.out.persistence;

import com.daniellaera.inventoryservice.domain.port.out.CompensationLedger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CompensationLedgerPersistenceAdapter implements CompensationLedger {

    private final CompensationLogJpaRepository compensationLogJpaRepository;

    @Override
    public boolean isCompensated(long orderId) {
        return compensationLogJpaRepository.existsByOrderId(orderId);
    }

    @Override
    public void record(long orderId, String productName, int quantity) {
        CompensationLogJpaEntity entry = new CompensationLogJpaEntity();
        entry.setOrderId(orderId);
        entry.setProductName(productName);
        entry.setQuantity(quantity);
        compensationLogJpaRepository.save(entry);
    }
}
