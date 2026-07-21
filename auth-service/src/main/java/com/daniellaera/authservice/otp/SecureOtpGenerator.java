package com.daniellaera.authservice.otp;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class SecureOtpGenerator implements OtpGenerator {

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public OtpCode generate() {
        int code = 100000 + secureRandom.nextInt(900000);
        return new OtpCode(String.valueOf(code));
    }
}
