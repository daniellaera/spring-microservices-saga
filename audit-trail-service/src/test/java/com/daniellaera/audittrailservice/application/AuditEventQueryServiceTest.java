package com.daniellaera.audittrailservice.application;

import com.daniellaera.audittrailservice.application.fakes.InMemoryAuditEventRepository;
import com.daniellaera.audittrailservice.application.fakes.InMemoryAuditEventRepository.Search;
import com.daniellaera.audittrailservice.domain.model.AuditEvent;
import com.daniellaera.audittrailservice.domain.model.AuditEventFilter;
import com.daniellaera.audittrailservice.domain.model.AuditEventType;
import com.daniellaera.audittrailservice.domain.model.PageQuery;
import com.daniellaera.audittrailservice.domain.model.PageResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuditEventQueryServiceTest {

    private static final AuditEvent ORDER_CREATED = new AuditEvent(1L, AuditEventType.ORDER_CREATED, "buyer@test.com",
            "ORDER", "42", "{}", "order-service", "42", null);

    private final InMemoryAuditEventRepository repository = new InMemoryAuditEventRepository();
    private final AuditEventQueryService service = new AuditEventQueryService(repository);

    @Test
    void search_asksTheStoreForThatPageWithTheFilter() {
        repository.answer(List.of(ORDER_CREATED));
        AuditEventFilter filter = new AuditEventFilter("42", List.of(AuditEventType.ORDER_CREATED), null, null, null,
                null, null);

        PageResult<AuditEvent> result = service.search(filter, new PageQuery(2, 10));

        assertThat(result.content()).containsExactly(ORDER_CREATED);
        assertThat(repository.queries()).containsExactly(new Search(filter, new PageQuery(2, 10)));
    }

    @Test
    void timeline_userAndOrderEvents_comeFromTheStore() {
        repository.answer(List.of(ORDER_CREATED));

        assertThat(service.timelineOf("42")).containsExactly(ORDER_CREATED);
        assertThat(service.eventsOfUser("buyer@test.com")).containsExactly(ORDER_CREATED);
        assertThat(service.eventsOfOrder("42")).containsExactly(ORDER_CREATED);
        assertThat(repository.queries()).containsExactly("timeline 42", "user buyer@test.com", "order 42");
    }
}
