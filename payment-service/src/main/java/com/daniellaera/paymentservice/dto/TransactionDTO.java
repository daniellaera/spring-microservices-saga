package com.daniellaera.paymentservice.dto;

import com.daniellaera.paymentservice.enums.PaymentStatus;

public record TransactionDTO(Long id, Long orderId, PaymentStatus status) {}