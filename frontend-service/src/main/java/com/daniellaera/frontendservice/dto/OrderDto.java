package com.daniellaera.frontendservice.dto;

public record OrderDto(Long id, String productName, Integer quantity, String status) {}
