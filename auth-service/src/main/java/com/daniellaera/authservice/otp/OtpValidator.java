package com.daniellaera.authservice.otp;

public interface OtpValidator {
    void validate(String email, String otp);
}
