package com.daniellaera.audittrailservice.adapter.in.messaging;

import com.daniellaera.audittrailservice.domain.port.in.RecordAuditEventCommand;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AuditConsumerTest {

    private final List<RecordAuditEventCommand> recorded = new ArrayList<>();
    private final AuditConsumer consumer = new AuditConsumer(recorded::add, JsonMapper.builder().build());

    @Test
    void aMessage_isRecordedWithEveryField_butTheProducersTimestamp() {
        consumer.consume("""
                {"eventType":"STOCK_DEDUCTED","userEmail":"buyer@test.com","entityType":"PRODUCT","entityId":"3",
                 "payload":"{}","serviceName":"inventory-service","timestamp":"2026-10-25T08:00:00",
                 "correlationId":"66"}""");

        assertThat(recorded).containsExactly(new RecordAuditEventCommand("STOCK_DEDUCTED", "buyer@test.com",
                "PRODUCT", "3", "{}", "inventory-service", "66"));
    }

    @Test
    void anUnreadableMessage_isSwallowed() {
        assertThatCode(() -> consumer.consume("not-valid-json")).doesNotThrowAnyException();

        assertThat(recorded).isEmpty();
    }
}
