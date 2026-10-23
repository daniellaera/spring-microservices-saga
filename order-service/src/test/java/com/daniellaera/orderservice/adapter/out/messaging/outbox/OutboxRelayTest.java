package com.daniellaera.orderservice.adapter.out.messaging.outbox;

import com.daniellaera.orderservice.adapter.out.messaging.KafkaOrderEventSender;
import com.daniellaera.orderservice.adapter.out.messaging.OrderEvent;
import com.daniellaera.orderservice.adapter.out.messaging.OrderEventNotSentException;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OutboxRelayTest {

    private final OutboxEventJpaRepository outboxRepository = mock(OutboxEventJpaRepository.class);
    private final KafkaOrderEventSender sender = mock(KafkaOrderEventSender.class);
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final OutboxRelay relay = new OutboxRelay(outboxRepository, sender, objectMapper);

    @Test
    void publishesPendingEventsAndMarksThemPublished() {
        OutboxEventJpaEntity row = row("{\"orderId\":42,\"productName\":\"Item\",\"quantity\":1,\"price\":10.00,"
                + "\"totalAmount\":10.00,\"userEmail\":\"user@test.com\",\"paymentIntentId\":null,\"items\":[]}");
        when(outboxRepository.findByPublishedFalseOrderByCreatedAtAsc()).thenReturn(List.of(row));

        relay.publishOutboxEvents();

        verify(sender).send(new OrderEvent(42L, "Item", 1, new BigDecimal("10.00"), new BigDecimal("10.00"),
                "user@test.com", null, List.of()));
        assertThat(row.isPublished()).isTrue();
        assertThat(row.getPublishedAt()).isNotNull();
        verify(outboxRepository).save(row);
    }

    @Test
    void unreadablePayload_staysUnpublishedAndDoesNotStopTheBatch() {
        OutboxEventJpaEntity broken = row("not json");
        OutboxEventJpaEntity good = row("{\"orderId\":43,\"items\":[]}");
        when(outboxRepository.findByPublishedFalseOrderByCreatedAtAsc()).thenReturn(List.of(broken, good));

        relay.publishOutboxEvents();

        assertThat(broken.isPublished()).isFalse();
        assertThat(broken.getPublishedAt()).isNull();
        assertThat(good.isPublished()).isTrue();
        verify(sender, times(1)).send(any());
    }

    @Test
    void sendFailure_leavesTheRowUnpublishedAndStopsTheBatch() {
        OutboxEventJpaEntity first = row(payload(44L));
        OutboxEventJpaEntity failing = row(payload(45L));
        OutboxEventJpaEntity later = row(payload(46L));
        when(outboxRepository.findByPublishedFalseOrderByCreatedAtAsc()).thenReturn(List.of(first, failing, later));
        doNothing().doThrow(new OrderEventNotSentException(45L, new RuntimeException("broker down")))
                .when(sender).send(any());

        relay.publishOutboxEvents();

        assertThat(first.isPublished()).isTrue();
        assertThat(failing.isPublished()).isFalse();
        assertThat(failing.getPublishedAt()).isNull();
        // a later event must not overtake the one that failed
        assertThat(later.isPublished()).isFalse();
        verify(sender, times(2)).send(any());
        verify(outboxRepository, never()).save(failing);
        verify(outboxRepository, never()).save(later);
    }

    @Test
    void failedRow_isRetriedOnTheNextTickBeforeLaterRows() {
        List<OutboxEventJpaEntity> outbox = List.of(row(payload(47L)), row(payload(48L)));
        when(outboxRepository.findByPublishedFalseOrderByCreatedAtAsc())
                .thenAnswer(invocation -> outbox.stream().filter(row -> !row.isPublished()).toList());
        doThrow(new OrderEventNotSentException(47L, new RuntimeException("broker down")))
                .doNothing()
                .when(sender).send(any());

        relay.publishOutboxEvents();
        relay.publishOutboxEvents();

        assertThat(outbox).allMatch(OutboxEventJpaEntity::isPublished);
        InOrder inOrder = inOrder(sender);
        inOrder.verify(sender, times(2)).send(argThat(event -> event.orderId() == 47L));
        inOrder.verify(sender).send(argThat(event -> event.orderId() == 48L));
    }

    @Test
    void nothingPending_sendsNothing() {
        when(outboxRepository.findByPublishedFalseOrderByCreatedAtAsc()).thenReturn(List.of());

        relay.publishOutboxEvents();

        verifyNoInteractions(sender);
    }

    private static String payload(long orderId) {
        return "{\"orderId\":" + orderId + ",\"items\":[]}";
    }

    private static OutboxEventJpaEntity row(String payload) {
        OutboxEventJpaEntity row = new OutboxEventJpaEntity();
        row.setAggregateId(42L);
        row.setEventType("ORDER_CREATED");
        row.setPayload(payload);
        return row;
    }
}
