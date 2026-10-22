package com.daniellaera.orderservice.adapter.in.web;

import com.daniellaera.orderservice.adapter.in.web.dto.OrderDTO;
import com.daniellaera.orderservice.adapter.in.web.dto.OrderItemDTO;
import com.daniellaera.orderservice.adapter.in.web.dto.OrderRequest;
import com.daniellaera.orderservice.adapter.in.web.dto.PagedResponse;
import com.daniellaera.orderservice.domain.model.Order;
import com.daniellaera.orderservice.domain.model.PageResult;
import com.daniellaera.orderservice.domain.port.in.PlaceOrderCommand;

import java.util.List;

public final class OrderWebMapper {

    private OrderWebMapper() {
    }

    public static PlaceOrderCommand toCommand(OrderRequest request, String userEmail) {
        List<PlaceOrderCommand.Line> lines = request.resolvedItems().stream()
                .map(i -> new PlaceOrderCommand.Line(i.productName(), i.quantity(), i.price()))
                .toList();
        return new PlaceOrderCommand(userEmail, request.paymentIntentId(), lines);
    }

    public static OrderDTO toDto(Order o) {
        List<OrderItemDTO> items = o.items().stream()
                .map(i -> new OrderItemDTO(i.id(), i.productName(), i.quantity(), i.price(), i.totalAmount()))
                .toList();

        return new OrderDTO(o.id(), o.productName(), o.quantity(), o.price(), o.totalAmount(), o.status(),
                o.userEmail(), o.createdAt(), o.paymentIntentId(), items);
    }

    public static PagedResponse<OrderDTO> toPagedResponse(PageResult<Order> result) {
        return new PagedResponse<>(
                result.content().stream().map(OrderWebMapper::toDto).toList(),
                result.page(),
                result.totalPages(),
                result.totalElements(),
                result.hasNext(),
                result.hasPrevious()
        );
    }
}
