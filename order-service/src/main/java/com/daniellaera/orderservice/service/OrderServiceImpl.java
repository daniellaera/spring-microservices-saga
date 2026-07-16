package com.daniellaera.orderservice.service;

import com.daniellaera.orderservice.dto.OrderDTO;
import com.daniellaera.orderservice.dto.OrderEvent;
import com.daniellaera.orderservice.dto.OrderItemDTO;
import com.daniellaera.orderservice.dto.OrderItemEvent;
import com.daniellaera.orderservice.dto.OrderItemRequest;
import com.daniellaera.orderservice.dto.OrderRequest;
import com.daniellaera.orderservice.dto.PagedResponse;
import com.daniellaera.orderservice.enums.OrderStatus;
import com.daniellaera.orderservice.exception.ResourceNotFoundException;
import com.daniellaera.orderservice.model.Order;
import com.daniellaera.orderservice.model.OrderItem;
import com.daniellaera.orderservice.model.OutboxEvent;
import com.daniellaera.orderservice.audit.AuditPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.daniellaera.orderservice.producer.OrderProducer;
import com.daniellaera.orderservice.repository.OrderRepository;
import com.daniellaera.orderservice.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
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
    private final AuditPublisher auditPublisher;

    @Override
    @Transactional
    public OrderDTO createOrder(OrderRequest request, String userEmail) {
        List<OrderItemRequest> itemRequests = request.resolvedItems();
        if (itemRequests.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order must have at least one item");
        }

        BigDecimal totalAmount = itemRequests.stream()
                .map(i -> i.price().multiply(BigDecimal.valueOf(i.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        OrderItemRequest firstItem = itemRequests.get(0);

        Order order = new Order();
        order.setProductName(firstItem.productName());
        order.setQuantity(firstItem.quantity());
        order.setPrice(firstItem.price());
        order.setTotalAmount(totalAmount);
        order.setStatus(OrderStatus.PENDING);
        order.setUserEmail(userEmail);
        order.setPaymentIntentId(request.paymentIntentId());

        List<OrderItem> orderItems = itemRequests.stream()
                .map(itemReq -> {
                    OrderItem item = new OrderItem();
                    item.setOrder(order);
                    item.setProductName(itemReq.productName());
                    item.setQuantity(itemReq.quantity());
                    item.setPrice(itemReq.price());
                    item.setTotalAmount(itemReq.price().multiply(BigDecimal.valueOf(itemReq.quantity())));
                    return item;
                })
                .toList();
        order.setItems(orderItems);

        Order saved = orderRepository.save(order);

        List<OrderItemEvent> itemEvents = saved.getItems().stream()
                .map(i -> new OrderItemEvent(i.getProductName(), i.getQuantity(), i.getPrice(), i.getTotalAmount()))
                .toList();

        OrderEvent orderEvent = new OrderEvent(saved.getId(), saved.getProductName(), saved.getQuantity(),
                saved.getPrice(), saved.getTotalAmount(), saved.getUserEmail(), saved.getPaymentIntentId(), itemEvents);

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

        auditPublisher.publish("ORDER_CREATED", saved.getUserEmail(), "ORDER",
                String.valueOf(saved.getId()), orderEvent);

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
    public OrderDTO getOrderById(Long id, String userEmail, String userRole) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
        boolean isAdmin = "ADMIN".equals(userRole);
        if (!isAdmin && !order.getUserEmail().equals(userEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        return toDTO(order);
    }

    @Override
    public List<OrderDTO> getAllOrders() {
        return orderRepository.findAll()
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    public List<OrderDTO> getMyOrders(String userEmail) {
        return orderRepository.findByUserEmail(userEmail)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    @Override
    public PagedResponse<OrderDTO> getMyOrdersPaged(String userEmail, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> result = orderRepository.findByUserEmail(userEmail, pageable);
        return new PagedResponse<>(
                result.getContent().stream().map(this::toDTO).toList(),
                result.getNumber(),
                result.getTotalPages(),
                result.getTotalElements(),
                result.hasNext(),
                result.hasPrevious()
        );
    }

    @Override
    public PagedResponse<OrderDTO> getAllOrdersPaged(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> result = orderRepository.findAll(pageable);
        return new PagedResponse<>(
                result.getContent().stream().map(this::toDTO).toList(),
                result.getNumber(),
                result.getTotalPages(),
                result.getTotalElements(),
                result.hasNext(),
                result.hasPrevious()
        );
    }

    private OrderDTO toDTO(Order o) {
        List<OrderItemDTO> items = o.getItems() == null
                ? List.of()
                : o.getItems().stream()
                    .map(i -> new OrderItemDTO(i.getId(), i.getProductName(), i.getQuantity(), i.getPrice(), i.getTotalAmount()))
                    .toList();

        return new OrderDTO(o.getId(), o.getProductName(), o.getQuantity(), o.getPrice(), o.getTotalAmount(), o.getStatus(), o.getUserEmail(), o.getCreatedAt(), o.getPaymentIntentId(), items);
    }
}
