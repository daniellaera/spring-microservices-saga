package com.daniellaera.orderservice.adapter.out.messaging.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventJpaRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {
    // published = false as well: a row the previous image published has status PENDING still
    List<OutboxEventJpaEntity> findByPublishedFalseAndStatusOrderByCreatedAtAsc(OutboxStatus status);
}
