package com.daniellaera.orderservice.adapter.out.messaging.outbox;

import com.daniellaera.orderservice.adapter.out.messaging.KafkaOrderEventSender;
import com.daniellaera.orderservice.adapter.out.messaging.OrderEvent;
import com.daniellaera.orderservice.adapter.out.messaging.OrderEventNotSentException;
import com.daniellaera.orderservice.application.fakes.RecordingAuditTrail;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OutboxRelayTest {

    private static final int MAX_ATTEMPTS = 3;

    private final OutboxEventJpaRepository outboxRepository = mock(OutboxEventJpaRepository.class);
    private final KafkaOrderEventSender sender = mock(KafkaOrderEventSender.class);
    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final RecordingAuditTrail auditTrail = new RecordingAuditTrail();
    private final OutboxRelay relay = new OutboxRelay(outboxRepository, sender, objectMapper, auditTrail, MAX_ATTEMPTS);

    /** The table: the stubbed finder returns what the real query would, in insertion order. */
    private final List<OutboxEventJpaEntity> outbox = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(outboxRepository.findByPublishedFalseAndStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenAnswer(invocation -> outbox.stream()
                        .filter(row -> !row.isPublished() && row.getStatus() == OutboxStatus.PENDING)
                        .toList());
    }

    @Test
    void publishesPendingEventsAndMarksThemPublished() {
        OutboxEventJpaEntity row = row("{\"orderId\":42,\"productName\":\"Item\",\"quantity\":1,\"price\":10.00,"
                + "\"totalAmount\":10.00,\"userEmail\":\"user@test.com\",\"paymentIntentId\":null,\"items\":[]}");

        relay.publishOutboxEvents();

        verify(sender).send(new OrderEvent(42L, "Item", 1, new BigDecimal("10.00"), new BigDecimal("10.00"),
                "user@test.com", null, List.of()));
        assertThat(row.isPublished()).isTrue();
        assertThat(row.getPublishedAt()).isNotNull();
        assertThat(row.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(row.getAttempts()).isZero();
        verify(outboxRepository).save(row);
    }

    @Test
    void unreadablePayload_countsAnAttemptAndDoesNotStopTheBatch() {
        OutboxEventJpaEntity broken = row("not json");
        OutboxEventJpaEntity good = row(payload(43L));

        relay.publishOutboxEvents();

        assertThat(broken.isPublished()).isFalse();
        assertThat(broken.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(broken.getAttempts()).isEqualTo(1);
        assertThat(broken.getLastError()).startsWith("unreadable payload: ");
        assertThat(good.isPublished()).isTrue();
        verify(sender, times(1)).send(any());
        assertThat(auditTrail.outboxFailures()).isEmpty();
    }

    @Test
    void unreadablePayload_isFailedAfterMaxAttempts_auditedOnce_andNoLongerTried() {
        OutboxEventJpaEntity broken = row("not json");

        for (int tick = 0; tick < MAX_ATTEMPTS + 2; tick++) {
            relay.publishOutboxEvents();
        }

        assertThat(broken.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(broken.isPublished()).isFalse();
        assertThat(broken.getAttempts()).isEqualTo(MAX_ATTEMPTS);
        assertThat(auditTrail.outboxFailures()).singleElement().satisfies(failure -> {
            assertThat(failure.orderId()).isEqualTo(42L);
            assertThat(failure.userEmail()).isNull();
            assertThat(failure.eventType()).isEqualTo("ORDER_CREATED");
            assertThat(failure.attempts()).isEqualTo(MAX_ATTEMPTS);
            assertThat(failure.error()).startsWith("unreadable payload: ");
        });
        verifyNoInteractions(sender);
    }

    @Test
    void transientSendFailure_neverCounts_evenWhenItOutlastsMaxAttempts() {
        OutboxEventJpaEntity first = row(payload(44L));
        OutboxEventJpaEntity later = row(payload(45L));
        doThrow(notSent(44L, new TimeoutException("Expiring 1 record(s)"))).when(sender).send(any());

        for (int tick = 0; tick < MAX_ATTEMPTS * 3; tick++) {
            relay.publishOutboxEvents();
        }

        assertThat(List.of(first, later)).allSatisfy(row -> {
            assertThat(row.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(row.getAttempts()).isZero();
            assertThat(row.isPublished()).isFalse();
        });
        // the batch stops at the head every tick: a later event never overtakes it
        verify(sender, never()).send(argThat(event -> event.orderId() == 45L));
        verify(outboxRepository, never()).save(any());
        assertThat(auditTrail.outboxFailures()).isEmpty();
    }

    @Test
    void failedRow_isRetriedOnTheNextTickBeforeLaterRows() {
        row(payload(47L));
        row(payload(48L));
        doThrow(notSent(47L, new TimeoutException("broker down")))
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
    void recordKafkaRefuses_countsAndStopsTheBatch_thenFailsAndLetsLaterRowsThrough() {
        OutboxEventJpaEntity tooLarge = row(payload(49L, "big@test.com"));
        OutboxEventJpaEntity later = row(payload(50L));
        doThrow(notSent(49L, new RecordTooLargeException("The message is 2000000 bytes")))
                .when(sender).send(argThat(event -> event.orderId() == 49L));

        relay.publishOutboxEvents();

        assertThat(tooLarge.getAttempts()).isEqualTo(1);
        assertThat(tooLarge.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(later.isPublished()).isFalse();

        for (int tick = 1; tick < MAX_ATTEMPTS; tick++) {
            relay.publishOutboxEvents();
        }
        assertThat(tooLarge.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(later.isPublished()).isFalse();
        assertThat(auditTrail.outboxFailures()).singleElement().satisfies(failure -> {
            assertThat(failure.userEmail()).isEqualTo("big@test.com");
            assertThat(failure.attempts()).isEqualTo(MAX_ATTEMPTS);
            assertThat(failure.error()).contains("RecordTooLargeException");
        });

        relay.publishOutboxEvents();
        assertThat(later.isPublished()).isTrue();
        verify(sender, times(MAX_ATTEMPTS)).send(argThat(event -> event.orderId() == 49L));
    }

    @Test
    void nothingPending_sendsNothing() {
        relay.publishOutboxEvents();

        verifyNoInteractions(sender);
    }

    @Test
    void maxAttemptsBelowOne_isRefused() {
        assertThatThrownBy(() -> new OutboxRelay(outboxRepository, sender, objectMapper, auditTrail, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("outbox.relay.max-attempts must be at least 1, was 0");
    }

    private static OrderEventNotSentException notSent(long orderId, Exception cause) {
        return new OrderEventNotSentException(orderId, new java.util.concurrent.ExecutionException(cause));
    }

    private static String payload(long orderId) {
        return payload(orderId, "user@test.com");
    }

    private static String payload(long orderId, String userEmail) {
        return "{\"orderId\":" + orderId + ",\"userEmail\":\"" + userEmail + "\",\"items\":[]}";
    }

    private OutboxEventJpaEntity row(String payload) {
        OutboxEventJpaEntity row = new OutboxEventJpaEntity();
        row.setAggregateId(42L);
        row.setEventType("ORDER_CREATED");
        row.setPayload(payload);
        outbox.add(row);
        return row;
    }
}
