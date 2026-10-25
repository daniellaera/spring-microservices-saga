package com.daniellaera.inventoryservice.application;

import com.daniellaera.inventoryservice.application.fakes.RecordingAuditTrail;
import com.daniellaera.inventoryservice.application.fakes.RecordingAuditTrail.Audit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportDeadLetteredServiceTest {

    private final RecordingAuditTrail auditTrail = new RecordingAuditTrail();
    private final ReportDeadLetteredService service = new ReportDeadLetteredService(auditTrail);

    @Test
    void aDeadLetteredOrderAndPaymentResult_areAudited() {
        service.reservationDeadLettered(7L, "user@test.com", "database unavailable");
        service.restoreDeadLettered(8L, "user@test.com", "db down");
        service.reservationDeadLettered(null, null, "Unrecognized token 'not'");

        assertThat(auditTrail.audits()).containsExactly(
                new Audit("STOCK_RESERVATION_DEAD_LETTERED", null, "database unavailable", "user@test.com", 7L),
                new Audit("STOCK_RESTORE_DEAD_LETTERED", null, "db down", "user@test.com", 8L),
                new Audit("STOCK_RESERVATION_DEAD_LETTERED", null, "Unrecognized token 'not'", null, null));
    }
}
