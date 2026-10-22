package com.daniellaera.inventoryservice.domain.port.in;

import com.daniellaera.inventoryservice.domain.model.OrderLine;

import java.math.BigDecimal;
import java.util.List;

/**
 * An order to reserve stock for. productName, quantity and price are the legacy single-item fields of the
 * order event, echoed back in the inventory result; lines are the items to reserve, already resolved from
 * the legacy fields when the event had none.
 */
public record ReserveStockCommand(Long orderId, String productName, Integer quantity, BigDecimal price,
                                  BigDecimal totalAmount, String userEmail, String paymentIntentId,
                                  List<OrderLine> lines) {
}
