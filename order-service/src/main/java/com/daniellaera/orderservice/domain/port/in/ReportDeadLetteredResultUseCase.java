package com.daniellaera.orderservice.domain.port.in;

/**
 * A payment result ran out of retries and was dead-lettered: it was not applied, so its order stays PENDING until
 * someone replays it (docs/maintenance/replay-dead-lettered-orders-and-payment-results.md).
 */
public interface ReportDeadLetteredResultUseCase {

    /** orderId and userEmail are null when the dead-lettered message cannot be read. */
    void deadLettered(Long orderId, String userEmail, String error);
}
