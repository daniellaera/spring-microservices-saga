package com.daniellaera.frontendservice.dto;

import java.math.BigDecimal;

public record ProductDto(String name, Integer quantity, BigDecimal price) {}
