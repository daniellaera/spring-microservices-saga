package com.daniellaera.authservice.dto;

public record OtpInitiatedResponse(
    boolean requiresOtp,
    String email,
    String message
) {}
