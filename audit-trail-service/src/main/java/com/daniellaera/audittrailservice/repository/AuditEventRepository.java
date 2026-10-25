package com.daniellaera.audittrailservice.repository;

import com.daniellaera.audittrailservice.domain.model.AuditEventType;
import com.daniellaera.audittrailservice.model.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long>, JpaSpecificationExecutor<AuditEvent> {

    /**
     * Every event of one order's saga, oldest first. Events recorded before the correlation id existed are matched
     * the way they were stored: order and payment events by their entity id (except PAYMENT_INITIATED, whose entity
     * is the payment intent: the order did not exist yet), STOCK_RESTORED by the order id in its payload.
     * STOCK_DEDUCTED had no order id at all, so older orders show none.
     */
    @Query("""
            select e from AuditEvent e
            where e.correlationId = :orderId
               or (e.correlationId is null
                   and ((e.entityType in ('ORDER', 'PAYMENT') and e.entityId = :orderId and e.eventType <> :initiated)
                        or (e.eventType = :restored and e.payload like :restoredPayload)))
            order by e.createdAt, e.id
            """)
    List<AuditEvent> findTimeline(@Param("orderId") String orderId,
                                  @Param("initiated") AuditEventType initiated,
                                  @Param("restored") AuditEventType restored,
                                  @Param("restoredPayload") String restoredPayload);

    List<AuditEvent> findByUserEmailOrderByCreatedAtDesc(String userEmail);

    List<AuditEvent> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            String entityType, String entityId);
}
