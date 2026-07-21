package com.daniellaera.authservice.otp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisOtpValidator implements OtpValidator {

    private final OtpRepository otpRepository;

    @Override
    public void validate(String email, String otp) {
        String stored = otpRepository.find(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "OTP expired or not found — please login again"));

        if (!stored.equals(otp)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid OTP code");
        }

        otpRepository.delete(email);
        log.info("=== OTP validated and consumed for {}", email);
    }
}
