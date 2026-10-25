package com.daniellaera.orderservice.application;

import com.daniellaera.orderservice.application.fakes.RecordingAuditTrail;
import com.daniellaera.orderservice.application.fakes.RecordingAuditTrail.DeadLetter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportDeadLetteredResultServiceTest {

    private final RecordingAuditTrail auditTrail = new RecordingAuditTrail();
    private final ReportDeadLetteredResultService service = new ReportDeadLetteredResultService(auditTrail);

    @Test
    void aDeadLetteredPaymentResult_isAudited() {
        service.deadLettered(7L, "user@test.com", "db down");
        service.deadLettered(null, null, "Unexpected character");

        assertThat(auditTrail.deadLetters()).containsExactly(
                new DeadLetter(7L, "user@test.com", "db down"),
                new DeadLetter(null, null, "Unexpected character"));
    }
}
