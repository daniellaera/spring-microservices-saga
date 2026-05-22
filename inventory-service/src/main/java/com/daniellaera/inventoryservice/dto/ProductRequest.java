package com.daniellaera.inventoryservice.dto;

import java.math.BigDecimal;

public record ProductRequest(String name, Integer quantity, BigDecimal price) {}
