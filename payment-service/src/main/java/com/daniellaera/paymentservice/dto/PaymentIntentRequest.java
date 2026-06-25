package com.daniellaera.paymentservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PaymentIntentRequest(
    @NotNull(message = "Amount is required")
    @Min(value = 1, message = "Amount must be at least 1")
    Long amount,

    @NotBlank(message = "Currency is required")
    String currency,

    @NotBlank(message = "Product name is required")
    String productName
) {}
