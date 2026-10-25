package com.daniellaera.inventoryservice.application;

import com.daniellaera.inventoryservice.domain.port.in.ReportDeadLetteredUseCase;
import com.daniellaera.inventoryservice.domain.port.out.AuditTrail;

/** Makes a dead-lettered record visible in the audit trail, where a stuck order is looked for. */
public class ReportDeadLetteredService implements ReportDeadLetteredUseCase {

    private final AuditTrail auditTrail;

    public ReportDeadLetteredService(AuditTrail auditTrail) {
        this.auditTrail = auditTrail;
    }

    @Override
    public void reservationDeadLettered(Long orderId, String userEmail, String error) {
        auditTrail.stockReservationDeadLettered(orderId, userEmail, error);
    }

    @Override
    public void restoreDeadLettered(Long orderId, String userEmail, String error) {
        auditTrail.stockRestoreDeadLettered(orderId, userEmail, error);
    }
}
