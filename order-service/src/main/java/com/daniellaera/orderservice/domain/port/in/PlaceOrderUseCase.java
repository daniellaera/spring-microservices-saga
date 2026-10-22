package com.daniellaera.orderservice.domain.port.in;

import com.daniellaera.orderservice.domain.model.Order;

public interface PlaceOrderUseCase {
    Order placeOrder(PlaceOrderCommand command);
}
