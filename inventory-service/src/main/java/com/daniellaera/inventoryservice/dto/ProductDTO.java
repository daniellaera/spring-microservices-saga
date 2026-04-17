package com.daniellaera.inventoryservice.dto;

import java.time.LocalDateTime;

public record ProductDTO(Long id, String name, Integer quantity, LocalDateTime createdAt) {}
