package com.daniellaera.paymentservice.application;

import com.daniellaera.paymentservice.domain.port.in.ReportDeadLetteredResultUseCase;
import com.daniellaera.paymentservice.domain.port.out.AuditTrail;

/** Makes a dead-lettered inventory result visible in the audit trail, where a stuck order is looked for. */
public class ReportDeadLetteredResultService implements ReportDeadLetteredResultUseCase {

    private final AuditTrail auditTrail;

    public ReportDeadLetteredResultService(AuditTrail auditTrail) {
        this.auditTrail = auditTrail;
    }

    @Override
    public void deadLettered(Long orderId, String userEmail, String error) {
        auditTrail.paymentDeadLettered(orderId, userEmail, error);
    }
}
