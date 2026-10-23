package com.daniellaera.paymentservice.application;

import com.daniellaera.paymentservice.application.fakes.EffectLog;
import com.daniellaera.paymentservice.application.fakes.RecordingAuditTrail;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportDeadLetteredResultServiceTest {

    private final RecordingAuditTrail auditTrail = new RecordingAuditTrail(new EffectLog());
    private final ReportDeadLetteredResultService service = new ReportDeadLetteredResultService(auditTrail);

    @Test
    void deadLetteredResult_isAudited() {
        service.deadLettered(7L, "user@test.com", "Stripe unavailable: 503");

        assertThat(auditTrail.deadLettered())
                .containsExactly(new RecordingAuditTrail.DeadLettered(7L, "user@test.com", "Stripe unavailable: 503"));
    }
}
