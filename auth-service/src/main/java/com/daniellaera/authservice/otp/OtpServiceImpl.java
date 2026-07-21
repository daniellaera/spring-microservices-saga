package com.daniellaera.authservice.otp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpServiceImpl implements OtpService {

    private final OtpGenerator otpGenerator;
    private final OtpRepository otpRepository;
    private final OtpValidator otpValidator;
    private final OtpEventPublisher otpEventPublisher;

    private static final Duration OTP_TTL = Duration.ofMinutes(5);

    @Override
    public void initiateOtp(String email) {
        OtpCode otp = otpGenerator.generate();
        otpRepository.save(email, otp, OTP_TTL);
        otpEventPublisher.publishOtpGenerated(email, otp);
        log.info("=== OTP initiated for {}", email);
    }

    @Override
    public void verifyOtp(String email, String otp) {
        otpValidator.validate(email, otp);
    }
}
