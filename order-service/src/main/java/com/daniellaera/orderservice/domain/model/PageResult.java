package com.daniellaera.orderservice.domain.model;

import java.util.List;

public record PageResult<T>(List<T> content, int page, int totalPages, long totalElements,
                            boolean hasNext, boolean hasPrevious) {}
