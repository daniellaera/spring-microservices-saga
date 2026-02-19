package com.daniellaera.orderservice.dto;

public record OrderEvent(Long orderId, String productName, Integer quantity) {}