package com.daniellaera.inventoryservice.application.fakes;

import com.daniellaera.inventoryservice.domain.model.OrderLine;
import com.daniellaera.inventoryservice.domain.model.Product;
import com.daniellaera.inventoryservice.domain.model.StockReservation;
import com.daniellaera.inventoryservice.domain.port.out.AuditTrail;

import java.util.ArrayList;
import java.util.List;

/** Records every call; the after-commit behaviour belongs to the adapter and is tested there. */
public class RecordingAuditTrail implements AuditTrail {

    // orderId: the saga's correlation id the adapter sends with the event
    public record Audit(String eventType, Product product, Object payload, String userEmail, Long orderId) {}

    private final List<Audit> audits = new ArrayList<>();

    @Override
    public void stockDeducted(Product product, OrderLine line, Long orderId, String userEmail) {
        audits.add(new Audit("STOCK_DEDUCTED", snapshot(product), line, userEmail, orderId));
    }

    @Override
    public void stockRestored(Product product, StockReservation reservation, String userEmail) {
        audits.add(new Audit("STOCK_RESTORED", snapshot(product), reservation, userEmail, reservation.orderId()));
    }

    @Override
    public void productCreated(Product product) {
        audits.add(new Audit("PRODUCT_CREATED", snapshot(product), null, null, null));
    }

    @Override
    public void productRestocked(Product product) {
        audits.add(new Audit("PRODUCT_RESTOCKED", snapshot(product), null, null, null));
    }

    // the payload: the error
    @Override
    public void stockReservationDeadLettered(Long orderId, String userEmail, String error) {
        audits.add(new Audit("STOCK_RESERVATION_DEAD_LETTERED", null, error, userEmail, orderId));
    }

    @Override
    public void stockRestoreDeadLettered(Long orderId, String userEmail, String error) {
        audits.add(new Audit("STOCK_RESTORE_DEAD_LETTERED", null, error, userEmail, orderId));
    }

    public List<Audit> audits() {
        return List.copyOf(audits);
    }

    private static Product snapshot(Product p) {
        return Product.restore(p.id(), p.name(), p.quantity(), p.price(), p.createdAt());
    }
}
