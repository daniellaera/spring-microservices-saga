package com.daniellaera.orderservice.dto;

public record OrderStatusUpdate(Long orderId, String status, String userEmail) {}
