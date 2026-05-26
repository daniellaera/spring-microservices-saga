package com.daniellaera.orderservice.dto;

import java.util.List;

public record PagedResponse<T>(
        List<T> content,
        int currentPage,
        int totalPages,
        long totalElements,
        boolean hasNext,
        boolean hasPrevious
) {}
