package com.daniellaera.orderservice.service;

import com.daniellaera.orderservice.dto.OrderDTO;
import com.daniellaera.orderservice.dto.OrderItemDTO;
import com.daniellaera.orderservice.dto.OrderItemRequest;
import com.daniellaera.orderservice.dto.OrderRequest;
import com.daniellaera.orderservice.dto.PagedResponse;
import com.daniellaera.orderservice.domain.model.OrderStatus;
import com.daniellaera.orderservice.exception.ResourceNotFoundException;
import com.daniellaera.orderservice.adapter.out.persistence.OrderJpaEntity;
import com.daniellaera.orderservice.adapter.out.persistence.OrderItemJpaEntity;
import com.daniellaera.orderservice.adapter.out.persistence.OrderJpaMapper;
import com.daniellaera.orderservice.domain.model.Order;
import com.daniellaera.orderservice.domain.port.out.AuditTrail;
import com.daniellaera.orderservice.domain.port.out.OrderEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.daniellaera.orderservice.adapter.out.persistence.OrderJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderJpaRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;
    private final AuditTrail auditTrail;

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

        OrderJpaEntity order = new OrderJpaEntity();
        order.setProductName(firstItem.productName());
        order.setQuantity(firstItem.quantity());
        order.setPrice(firstItem.price());
        order.setTotalAmount(totalAmount);
        order.setStatus(OrderStatus.PENDING);
        order.setUserEmail(userEmail);
        order.setPaymentIntentId(request.paymentIntentId());

        List<OrderItemJpaEntity> orderItems = itemRequests.stream()
                .map(itemReq -> {
                    OrderItemJpaEntity item = new OrderItemJpaEntity();
                    item.setOrder(order);
                    item.setProductName(itemReq.productName());
                    item.setQuantity(itemReq.quantity());
                    item.setPrice(itemReq.price());
                    item.setTotalAmount(itemReq.price().multiply(BigDecimal.valueOf(itemReq.quantity())));
                    return item;
                })
                .toList();
        order.setItems(orderItems);

        OrderJpaEntity saved = orderRepository.save(order);

        Order placed = OrderJpaMapper.toDomain(saved);
        orderEventPublisher.orderPlaced(placed);
        auditTrail.orderPlaced(placed);

        return toDTO(saved);
    }

    @Override
    public OrderDTO getOrderById(Long id, String userEmail, String userRole) {
        OrderJpaEntity order = orderRepository.findById(id)
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
        Page<OrderJpaEntity> result = orderRepository.findByUserEmail(userEmail, pageable);
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
        Page<OrderJpaEntity> result = orderRepository.findAll(pageable);
        return new PagedResponse<>(
                result.getContent().stream().map(this::toDTO).toList(),
                result.getNumber(),
                result.getTotalPages(),
                result.getTotalElements(),
                result.hasNext(),
                result.hasPrevious()
        );
    }

    private OrderDTO toDTO(OrderJpaEntity o) {
        List<OrderItemDTO> items = o.getItems() == null
                ? List.of()
                : o.getItems().stream()
                    .map(i -> new OrderItemDTO(i.getId(), i.getProductName(), i.getQuantity(), i.getPrice(), i.getTotalAmount()))
                    .toList();

        return new OrderDTO(o.getId(), o.getProductName(), o.getQuantity(), o.getPrice(), o.getTotalAmount(), o.getStatus(), o.getUserEmail(), o.getCreatedAt(), o.getPaymentIntentId(), items);
    }
}
