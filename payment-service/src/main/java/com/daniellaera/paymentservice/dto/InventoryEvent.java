package com.daniellaera.paymentservice.dto;

public record InventoryEvent(Long orderId, String productName, Integer quantity, String status) {}
