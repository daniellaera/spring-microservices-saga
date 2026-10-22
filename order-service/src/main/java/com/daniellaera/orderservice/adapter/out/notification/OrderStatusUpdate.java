package com.daniellaera.orderservice.adapter.out.notification;

public record OrderStatusUpdate(Long orderId, String status, String userEmail) {}
