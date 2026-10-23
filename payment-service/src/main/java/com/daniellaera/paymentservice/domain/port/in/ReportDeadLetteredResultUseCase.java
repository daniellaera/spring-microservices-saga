package com.daniellaera.paymentservice.domain.port.in;

/**
 * An inventory result ran out of retries and was dead-lettered: nothing was recorded, so its order stays PENDING
 * until someone replays the result (docs/maintenance/replay-dead-lettered-inventory-results.md).
 */
public interface ReportDeadLetteredResultUseCase {

    /** orderId and userEmail are null when the dead-lettered message cannot be read. */
    void deadLettered(Long orderId, String userEmail, String error);
}
