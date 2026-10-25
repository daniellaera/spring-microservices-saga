package com.daniellaera.orderservice.application;

import com.daniellaera.orderservice.domain.port.in.ReportDeadLetteredResultUseCase;
import com.daniellaera.orderservice.domain.port.out.AuditTrail;

/** Makes a dead-lettered payment result visible in the audit trail, where a stuck order is looked for. */
public class ReportDeadLetteredResultService implements ReportDeadLetteredResultUseCase {

    private final AuditTrail auditTrail;

    public ReportDeadLetteredResultService(AuditTrail auditTrail) {
        this.auditTrail = auditTrail;
    }

    @Override
    public void deadLettered(Long orderId, String userEmail, String error) {
        auditTrail.paymentResultDeadLettered(orderId, userEmail, error);
    }
}
