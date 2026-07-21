package com.daniellaera.notificationservice.dto;

public record OtpMessage(
        String email,
        String otp,
        String timestamp
) {}
