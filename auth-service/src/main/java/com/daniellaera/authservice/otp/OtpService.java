package com.daniellaera.authservice.otp;

public interface OtpService {
    void initiateOtp(String email);
    void verifyOtp(String email, String otp);
}
