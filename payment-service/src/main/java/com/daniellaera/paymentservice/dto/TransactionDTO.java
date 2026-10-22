package com.daniellaera.paymentservice.dto;

import com.daniellaera.paymentservice.domain.model.PaymentStatus;

import java.math.BigDecimal;

public record TransactionDTO(Long id, Long orderId, BigDecimal totalAmount, PaymentStatus status) {}