package com.daniellaera.orderservice.adapter.out.persistence;

import com.daniellaera.orderservice.domain.model.Order;
import com.daniellaera.orderservice.domain.model.OrderItem;

import java.util.List;

public final class OrderJpaMapper {

    private OrderJpaMapper() {
    }

    public static Order toDomain(OrderJpaEntity entity) {
        List<OrderItem> items = entity.getItems() == null
                ? List.of()
                : entity.getItems().stream()
                    .map(i -> new OrderItem(i.getId(), i.getProductName(), i.getQuantity(), i.getPrice(), i.getTotalAmount()))
                    .toList();

        return Order.restore(entity.getId(), entity.getUserEmail(), entity.getPaymentIntentId(), entity.getStatus(),
                entity.getTotalAmount(), entity.getCreatedAt(),
                entity.getProductName(), entity.getQuantity(), entity.getPrice(), items);
    }

    /** Maps an order that has never been persisted to a new entity graph. */
    public static OrderJpaEntity toNewEntity(Order order) {
        OrderJpaEntity entity = new OrderJpaEntity();
        entity.setProductName(order.productName());
        entity.setQuantity(order.quantity());
        entity.setPrice(order.price());
        entity.setTotalAmount(order.totalAmount());
        entity.setStatus(order.status());
        entity.setUserEmail(order.userEmail());
        entity.setPaymentIntentId(order.paymentIntentId());
        entity.setItems(order.items().stream()
                .map(i -> {
                    OrderItemJpaEntity item = new OrderItemJpaEntity();
                    item.setOrder(entity);
                    item.setProductName(i.productName());
                    item.setQuantity(i.quantity());
                    item.setPrice(i.price());
                    item.setTotalAmount(i.totalAmount());
                    return item;
                })
                .toList());
        return entity;
    }
}
