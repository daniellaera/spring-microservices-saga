package com.daniellaera.inventoryservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductDTO(Long id, String name, Integer quantity, BigDecimal price, LocalDateTime createdAt) {}
