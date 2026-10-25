package com.daniellaera.notificationservice.adapter.in.messaging;

public record OtpMessage(
        String email,
        String otp,
        String timestamp
) {}
