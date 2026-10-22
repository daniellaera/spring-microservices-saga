package com.daniellaera.orderservice.adapter.in.web;

import com.daniellaera.orderservice.adapter.in.web.dto.OrderDTO;
import com.daniellaera.orderservice.adapter.in.web.dto.OrderRequest;
import com.daniellaera.orderservice.adapter.in.web.dto.PagedResponse;
import com.daniellaera.orderservice.domain.model.PageQuery;
import com.daniellaera.orderservice.domain.port.in.GetOrderQuery;
import com.daniellaera.orderservice.domain.port.in.ListOrdersQuery;
import com.daniellaera.orderservice.domain.port.in.PlaceOrderUseCase;
import com.daniellaera.orderservice.domain.port.in.Requester;
import com.daniellaera.orderservice.adapter.out.notification.SseEmitterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final PlaceOrderUseCase placeOrderUseCase;
    private final GetOrderQuery getOrderQuery;
    private final ListOrdersQuery listOrdersQuery;
    private final SseEmitterRegistry sseEmitterRegistry;

    @GetMapping("/my")
    public ResponseEntity<PagedResponse<OrderDTO>> getMyOrders(
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        if (userEmail == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(OrderWebMapper.toPagedResponse(
                listOrdersQuery.listOrdersOf(userEmail, new PageQuery(page, size))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDTO> getOrder(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String userRole) {
        Requester requester = new Requester(userEmail, "ADMIN".equals(userRole));
        return ResponseEntity.ok(OrderWebMapper.toDto(getOrderQuery.getOrder(id, requester)));
    }

    @GetMapping
    public ResponseEntity<PagedResponse<OrderDTO>> getAllOrders(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return ResponseEntity.ok(OrderWebMapper.toPagedResponse(listOrdersQuery.listAllOrders(new PageQuery(page, size))));
    }

    @PostMapping
    public ResponseEntity<OrderDTO> createOrder(
            @Valid @RequestBody OrderRequest request,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail) {
        return ResponseEntity.ok(OrderWebMapper.toDto(
                placeOrderUseCase.placeOrder(OrderWebMapper.toCommand(request, userEmail))));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamOrders(
            @RequestHeader(value = "X-User-Email", required = false) String userEmail) {
        if (userEmail == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return sseEmitterRegistry.register(userEmail);
    }
}