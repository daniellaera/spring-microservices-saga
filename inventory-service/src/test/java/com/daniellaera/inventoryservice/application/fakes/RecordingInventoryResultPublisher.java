package com.daniellaera.inventoryservice.application.fakes;

import com.daniellaera.inventoryservice.domain.model.ReservationDecision;
import com.daniellaera.inventoryservice.domain.port.in.ReserveStockCommand;
import com.daniellaera.inventoryservice.domain.port.out.InventoryResultPublisher;

import java.util.ArrayList;
import java.util.List;

public class RecordingInventoryResultPublisher implements InventoryResultPublisher {

    public record Published(ReserveStockCommand command, ReservationDecision decision) {}

    private final List<Published> published = new ArrayList<>();

    @Override
    public void publish(ReserveStockCommand command, ReservationDecision decision) {
        published.add(new Published(command, decision));
    }

    public List<Published> published() {
        return List.copyOf(published);
    }
}
