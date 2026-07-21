package com.daniellaera.authservice.otp;

public interface OtpEventPublisher {
    void publishOtpGenerated(String email, OtpCode otp);
}
