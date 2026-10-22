package com.daniellaera.orderservice.domain.port.out;

import com.daniellaera.orderservice.domain.model.Order;

/** Publishes order events through the transactional outbox, in the caller's transaction. */
public interface OrderEventPublisher {
    void orderPlaced(Order order);
}
