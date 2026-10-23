package com.daniellaera.orderservice.adapter.out.messaging.outbox;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
public class OutboxEventJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private Long aggregateId;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private boolean published = false;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant publishedAt;

    @Column(nullable = false)
    private int attempts = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String lastError;

    void markPublished(Instant at) {
        published = true;
        publishedAt = at;
        status = OutboxStatus.PUBLISHED;
    }

    /** Counts a failed delivery attempt; the row is FAILED once it reaches maxAttempts. Returns whether it is. */
    boolean recordFailedAttempt(String error, int maxAttempts) {
        attempts++;
        lastError = error;
        if (attempts >= maxAttempts) {
            status = OutboxStatus.FAILED;
        }
        return status == OutboxStatus.FAILED;
    }
}
