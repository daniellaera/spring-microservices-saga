package com.daniellaera.orderservice.adapter.out.messaging;

import org.apache.kafka.common.InvalidRecordException;
import org.apache.kafka.common.errors.NotEnoughReplicasException;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.TopicAuthorizationException;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.KafkaException;

import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class OrderEventNotSentExceptionTest {

    @Test
    void aRecordKafkaRefusesAsSuch_isPermanent() {
        assertThat(notSent(new ExecutionException(new RecordTooLargeException("too large"))).permanent()).isTrue();
        assertThat(notSent(new ExecutionException(new InvalidRecordException("corrupt"))).permanent()).isTrue();
    }

    @Test
    void timeoutsAndBrokerOrClusterProblems_areNotPermanent() {
        assertThat(notSent(new TimeoutException()).permanent()).isFalse();
        assertThat(notSent(new KafkaException("Send failed",
                new org.apache.kafka.common.errors.TimeoutException("max.block.ms"))).permanent()).isFalse();
        assertThat(notSent(new ExecutionException(new NotEnoughReplicasException("isr"))).permanent()).isFalse();
        // not the record's fault: every row would fail the same way
        assertThat(notSent(new ExecutionException(new TopicAuthorizationException(Set.of("orders-topic"))))
                .permanent()).isFalse();
        assertThat(notSent(new InterruptedException()).permanent()).isFalse();
    }

    @Test
    void message_namesTheOrderAndTheRootCause() {
        assertThat(notSent(new ExecutionException(new RecordTooLargeException("too large"))))
                .hasMessage("Order event for orderId 7 was not sent: RecordTooLargeException too large");
    }

    private static OrderEventNotSentException notSent(Exception cause) {
        return new OrderEventNotSentException(7L, cause);
    }
}
