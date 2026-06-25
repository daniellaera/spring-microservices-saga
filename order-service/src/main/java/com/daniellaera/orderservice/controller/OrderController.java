package com.daniellaera.orderservice.controller;

import com.daniellaera.orderservice.dto.OrderDTO;
import com.daniellaera.orderservice.dto.OrderRequest;
import com.daniellaera.orderservice.dto.PagedResponse;
import com.daniellaera.orderservice.service.OrderService;
import com.daniellaera.orderservice.sse.SseEmitterRegistry;
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

    private final OrderService orderService;
    private final SseEmitterRegistry sseEmitterRegistry;

    @GetMapping("/my")
    public ResponseEntity<PagedResponse<OrderDTO>> getMyOrders(
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        if (userEmail == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(orderService.getMyOrdersPaged(userEmail, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderDTO> getOrder(@PathVariable("id") Long id) {
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    @GetMapping
    public ResponseEntity<PagedResponse<OrderDTO>> getAllOrders(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return ResponseEntity.ok(orderService.getAllOrdersPaged(page, size));
    }

    @PostMapping
    public ResponseEntity<OrderDTO> createOrder(
            @Valid @RequestBody OrderRequest request,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail) {
        return ResponseEntity.ok(orderService.createOrder(request, userEmail));
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