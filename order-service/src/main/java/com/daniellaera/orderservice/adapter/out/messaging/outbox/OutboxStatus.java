package com.daniellaera.orderservice.adapter.out.messaging.outbox;

public enum OutboxStatus {
    /** Waiting for the relay, possibly after failed attempts. */
    PENDING,
    /** Kafka acknowledged it. */
    PUBLISHED,
    /** Could not be sent after the maximum number of attempts; the relay no longer tries it. */
    FAILED
}
