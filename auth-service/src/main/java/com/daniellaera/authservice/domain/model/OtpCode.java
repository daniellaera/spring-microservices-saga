package com.daniellaera.authservice.domain.model;

public record OtpCode(String value) {
    public OtpCode {
        if (value == null || value.length() != 6) {
            throw new IllegalArgumentException("OTP must be exactly 6 digits");
        }
    }
}
