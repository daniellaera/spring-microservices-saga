package com.daniellaera.orderservice.domain.port.in;

import com.daniellaera.orderservice.domain.model.Order;

public interface GetOrderQuery {
    Order getOrder(long orderId, Requester requester);
}
