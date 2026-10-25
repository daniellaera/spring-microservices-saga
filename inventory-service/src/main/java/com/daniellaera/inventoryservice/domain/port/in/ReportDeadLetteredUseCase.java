package com.daniellaera.inventoryservice.domain.port.in;

/**
 * A record ran out of retries and was dead-lettered: nothing was done for its order until someone replays it
 * (docs/maintenance/replay-dead-lettered-orders-and-payment-results.md). orderId and userEmail are null when the
 * dead-lettered record cannot be read.
 */
public interface ReportDeadLetteredUseCase {

    /** An order whose stock was not reserved: no inventory result was sent, the order stays PENDING. */
    void reservationDeadLettered(Long orderId, String userEmail, String error);

    /** A payment result whose reserved stock was not released. */
    void restoreDeadLettered(Long orderId, String userEmail, String error);
}
