package com.daniellaera.inventoryservice.dto;

public record InventoryEvent(Long orderId, String productName, Integer quantity, String status) {}
