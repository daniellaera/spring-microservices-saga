package com.daniellaera.orderservice.domain.port.in;

import java.math.BigDecimal;
import java.util.List;

public record PlaceOrderCommand(String userEmail, String paymentIntentId, List<Line> lines) {

    public record Line(String productName, int quantity, BigDecimal price) {}
}
