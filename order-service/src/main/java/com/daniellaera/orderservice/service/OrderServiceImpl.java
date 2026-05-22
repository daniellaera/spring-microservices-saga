package com.daniellaera.orderservice.service;

import com.daniellaera.orderservice.dto.OrderDTO;
import com.daniellaera.orderservice.dto.OrderEvent;
import com.daniellaera.orderservice.dto.OrderRequest;
import com.daniellaera.orderservice.enums.OrderStatus;
import com.daniellaera.orderservice.exception.ResourceNotFoundException;
import com.daniellaera.orderservice.model.Order;
import com.daniellaera.orderservice.model.OutboxEvent;
import com.daniellaera.orderservice.producer.OrderProducer;
import com.daniellaera.orderservice.repository.OrderRepository;
import com.daniellaera.orderservice.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final OrderProducer orderProducer;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public OrderDTO createOrder(OrderRequest request) {
        BigDecimal price = request.price() != null ? request.price() : BigDecimal.ZERO;
        BigDecimal totalAmount = price.multiply(BigDecimal.valueOf(request.quantity()));

        Order order = new Order();
        order.setProductName(request.productName());
        order.setQuantity(request.quantity());
        order.setPrice(price);
        order.setTotalAmount(totalAmount);
        order.setStatus(OrderStatus.PENDING);
        Order saved = orderRepository.save(order);

        OrderEvent orderEvent = new OrderEvent(saved.getId(), saved.getProductName(), saved.getQuantity(),
                saved.getPrice(), saved.getTotalAmount());

        try {
            OutboxEvent outboxEvent = new OutboxEvent();
            outboxEvent.setAggregateId(saved.getId());
            outboxEvent.setEventType("ORDER_CREATED");
            outboxEvent.setPayload(objectMapper.writeValueAsString(orderEvent));
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            log.error("=== Outbox: failed to persist outbox event for orderId {}: {}", saved.getId(), e.getMessage());
            throw new RuntimeException("Failed to save outbox event", e);
        }

        return toDTO(saved);
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishOutboxEvents() {
        List<OutboxEvent> unpublished = outboxEventRepository.findByPublishedFalseOrderByCreatedAtAsc();

        for (OutboxEvent event : unpublished) {
            try {
                OrderEvent orderEvent = objectMapper.readValue(event.getPayload(), OrderEvent.class);
                orderProducer.sendOrderEvent(orderEvent);
                event.setPublished(true);
                event.setPublishedAt(Instant.now());
                outboxEventRepository.save(event);
                log.info("=== Outbox: published event for orderId: {}", event.getAggregateId());
            } catch (Exception e) {
                log.error("=== Outbox: failed to publish event {}: {}", event.getId(), e.getMessage());
            }
        }
    }

    @Override
    public OrderDTO getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
        return toDTO(order);
    }

    @Override
    public List<OrderDTO> getAllOrders() {
        return orderRepository.findAll()
                .stream()
                .map(this::toDTO)
                .toList();
    }

    private OrderDTO toDTO(Order o) {
        return new OrderDTO(o.getId(), o.getProductName(), o.getQuantity(), o.getPrice(), o.getTotalAmount(), o.getStatus());
    }
}
