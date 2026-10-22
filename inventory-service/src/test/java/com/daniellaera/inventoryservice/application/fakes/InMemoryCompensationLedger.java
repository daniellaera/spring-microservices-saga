package com.daniellaera.inventoryservice.application.fakes;

import com.daniellaera.inventoryservice.domain.port.out.CompensationLedger;

import java.util.ArrayList;
import java.util.List;

public class InMemoryCompensationLedger implements CompensationLedger {

    public record Entry(long orderId, String productName, int quantity) {}

    private final List<Entry> entries = new ArrayList<>();

    @Override
    public boolean isCompensated(long orderId) {
        return entries.stream().anyMatch(e -> e.orderId() == orderId);
    }

    @Override
    public void record(long orderId, String productName, int quantity) {
        entries.add(new Entry(orderId, productName, quantity));
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }
}
