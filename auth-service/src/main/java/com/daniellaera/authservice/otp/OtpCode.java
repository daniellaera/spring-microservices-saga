package com.daniellaera.authservice.otp;

public record OtpCode(String value) {
    public OtpCode {
        if (value == null || value.length() != 6) {
            throw new IllegalArgumentException("OTP must be exactly 6 digits");
        }
    }
}
