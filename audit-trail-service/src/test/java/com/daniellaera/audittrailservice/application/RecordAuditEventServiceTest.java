package com.daniellaera.audittrailservice.application;

import com.daniellaera.audittrailservice.application.fakes.InMemoryAuditEventRepository;
import com.daniellaera.audittrailservice.domain.model.AuditEvent;
import com.daniellaera.audittrailservice.domain.model.AuditEventType;
import com.daniellaera.audittrailservice.domain.port.in.RecordAuditEventCommand;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class RecordAuditEventServiceTest {

    private final InMemoryAuditEventRepository repository = new InMemoryAuditEventRepository();
    private final RecordAuditEventService service = new RecordAuditEventService(repository);

    @Test
    void aKnownEventType_isStoredWithEveryField() {
        service.record(new RecordAuditEventCommand("ORDER_CREATED", "buyer@test.com", "ORDER", "42", "{}",
                "order-service", "42"));

        assertThat(repository.saved()).singleElement().satisfies(event -> assertThat(event)
                .usingRecursiveComparison().ignoringFields("id", "createdAt")
                .isEqualTo(AuditEvent.of(AuditEventType.ORDER_CREATED, "buyer@test.com", "ORDER", "42", "{}",
                        "order-service", "42")));
    }

    @Test
    void aMessageWithoutCorrelationId_isStoredWithoutOne() {
        service.record(new RecordAuditEventCommand("USER_LOGIN", "buyer@test.com", "USER", "buyer@test.com", "{}",
                "auth-service", null));

        assertThat(repository.saved()).singleElement()
                .satisfies(event -> assertThat(event.correlationId()).isNull());
    }

    @Test
    void anUnknownEventType_isDropped_withoutThrowing() {
        assertThatCode(() -> service.record(new RecordAuditEventCommand("NOT_A_REAL_EVENT", "buyer@test.com",
                "ORDER", "42", "{}", "order-service", null))).doesNotThrowAnyException();

        assertThat(repository.saved()).isEmpty();
    }

    @Test
    void aMissingEventType_isDropped_withoutThrowing() {
        assertThatCode(() -> service.record(new RecordAuditEventCommand(null, "buyer@test.com", "ORDER", "42", "{}",
                "order-service", null))).doesNotThrowAnyException();

        assertThat(repository.saved()).isEmpty();
    }

    @Test
    void aStoreFailure_isSwallowed() {
        repository.failWith(new IllegalStateException("database down"));

        assertThatCode(() -> service.record(new RecordAuditEventCommand("ORDER_CREATED", "buyer@test.com", "ORDER",
                "42", "{}", "order-service", "42"))).doesNotThrowAnyException();
    }
}
