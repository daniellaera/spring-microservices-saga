package com.daniellaera.orderservice.application;

import com.daniellaera.orderservice.domain.model.Order;
import com.daniellaera.orderservice.domain.model.OrderItem;
import com.daniellaera.orderservice.domain.port.in.PlaceOrderCommand;
import com.daniellaera.orderservice.domain.port.in.PlaceOrderUseCase;
import com.daniellaera.orderservice.domain.port.out.AuditTrail;
import com.daniellaera.orderservice.domain.port.out.OrderEventPublisher;
import com.daniellaera.orderservice.domain.port.out.OrderRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public class PlaceOrderService implements PlaceOrderUseCase {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;
    private final AuditTrail auditTrail;

    public PlaceOrderService(OrderRepository orderRepository, OrderEventPublisher orderEventPublisher,
                             AuditTrail auditTrail) {
        this.orderRepository = orderRepository;
        this.orderEventPublisher = orderEventPublisher;
        this.auditTrail = auditTrail;
    }

    @Override
    @Transactional
    public Order placeOrder(PlaceOrderCommand command) {
        List<OrderItem> items = command.lines().stream()
                .map(line -> OrderItem.of(line.productName(), line.quantity(), line.price()))
                .toList();

        Order saved = orderRepository.save(Order.place(command.userEmail(), command.paymentIntentId(), items));
        orderEventPublisher.orderPlaced(saved);
        auditTrail.orderPlaced(saved);
        return saved;
    }
}
