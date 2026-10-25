package com.daniellaera.authservice.adapter.in.web.dto;

public record OtpInitiatedResponse(
    boolean requiresOtp,
    String email,
    String message
) {}
