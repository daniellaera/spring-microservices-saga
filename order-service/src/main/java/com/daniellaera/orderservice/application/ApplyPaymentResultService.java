package com.daniellaera.orderservice.application;

import com.daniellaera.orderservice.domain.model.Order;
import com.daniellaera.orderservice.domain.port.in.ApplyPaymentResultUseCase;
import com.daniellaera.orderservice.domain.port.in.PaymentResult;
import com.daniellaera.orderservice.domain.port.out.AuditTrail;
import com.daniellaera.orderservice.domain.port.out.OrderRepository;
import com.daniellaera.orderservice.domain.port.out.OrderStatusNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Deliberately not transactional: the status update commits on its own before the audit event and the
 * SSE push, so the UI never hears about a status that is not committed yet.
 */
public class ApplyPaymentResultService implements ApplyPaymentResultUseCase {

    private static final Logger log = LoggerFactory.getLogger(ApplyPaymentResultService.class);

    private final OrderRepository orderRepository;
    private final AuditTrail auditTrail;
    private final OrderStatusNotifier orderStatusNotifier;

    public ApplyPaymentResultService(OrderRepository orderRepository, AuditTrail auditTrail,
                                     OrderStatusNotifier orderStatusNotifier) {
        this.orderRepository = orderRepository;
        this.auditTrail = auditTrail;
        this.orderStatusNotifier = orderStatusNotifier;
    }

    @Override
    public void applyPaymentResult(PaymentResult result) {
        orderRepository.findById(result.orderId()).ifPresent(order -> {
            order.applyPaymentResult(result.succeeded());
            Order saved = orderRepository.save(order);
            log.info("=== Order: status updated to {} for orderId {}", saved.status(), saved.id());

            auditTrail.paymentResultApplied(saved, result);
            orderStatusNotifier.statusChanged(saved);
        });
    }
}
