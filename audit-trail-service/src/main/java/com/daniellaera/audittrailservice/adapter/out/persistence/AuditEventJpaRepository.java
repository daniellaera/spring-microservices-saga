package com.daniellaera.audittrailservice.adapter.out.persistence;

import com.daniellaera.audittrailservice.domain.model.AuditEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuditEventJpaRepository extends JpaRepository<AuditEventJpaEntity, Long>, JpaSpecificationExecutor<AuditEventJpaEntity> {

    /**
     * Every event of one order's saga, oldest first. Events recorded before the correlation id existed are matched
     * the way they were stored: order and payment events by their entity id (except PAYMENT_INITIATED, whose entity
     * is the payment intent: the order did not exist yet), STOCK_RESTORED by the order id in its payload.
     * STOCK_DEDUCTED had no order id at all, so older orders show none.
     */
    @Query("""
            select e from AuditEventJpaEntity e
            where e.correlationId = :orderId
               or (e.correlationId is null
                   and ((e.entityType in ('ORDER', 'PAYMENT') and e.entityId = :orderId and e.eventType <> :initiated)
                        or (e.eventType = :restored and e.payload like :restoredPayload)))
            order by e.createdAt, e.id
            """)
    List<AuditEventJpaEntity> findTimeline(@Param("orderId") String orderId,
                                  @Param("initiated") AuditEventType initiated,
                                  @Param("restored") AuditEventType restored,
                                  @Param("restoredPayload") String restoredPayload);

    List<AuditEventJpaEntity> findByUserEmailOrderByCreatedAtDesc(String userEmail);

    List<AuditEventJpaEntity> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            String entityType, String entityId);
}
