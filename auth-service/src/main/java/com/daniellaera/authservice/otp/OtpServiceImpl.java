package com.daniellaera.authservice.otp;

import com.daniellaera.authservice.domain.port.out.OtpGenerator;
import com.daniellaera.authservice.domain.port.out.OtpRepository;
import com.daniellaera.authservice.domain.port.out.OtpAttempts;
import com.daniellaera.authservice.domain.port.out.OtpEventPublisher;
import com.daniellaera.authservice.domain.model.OtpCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpServiceImpl implements OtpService {

    static final Duration OTP_TTL = Duration.ofMinutes(5);
    // at most MAX_CODES_ISSUED codes (so MAX_CODES_ISSUED × 5 guesses) per email in ISSUE_WINDOW
    static final int MAX_CODES_ISSUED = 5;
    static final Duration ISSUE_WINDOW = Duration.ofMinutes(15);

    private final OtpGenerator otpGenerator;
    private final OtpRepository otpRepository;
    private final OtpValidator otpValidator;
    private final OtpEventPublisher otpEventPublisher;
    private final OtpAttempts otpAttempts;

    // Called after the password check, so only someone who knows the password can use up the email's codes
    @Override
    public void initiateOtp(String email) {
        long issued = otpAttempts.recordIssued(email, ISSUE_WINDOW);
        if (issued > MAX_CODES_ISSUED) {
            log.warn("=== OTP refused for {}: {} codes issued in the last {} min", email, issued - 1,
                    ISSUE_WINDOW.toMinutes());
            throw new OtpLimitExceededException(OtpLimitExceededException.TOO_MANY_CODES_ISSUED,
                    "Too many login attempts — please try again in " + ISSUE_WINDOW.toMinutes() + " minutes");
        }
        OtpCode otp = otpGenerator.generate();
        otpRepository.save(email, otp, OTP_TTL);
        otpAttempts.resetFailures(email);
        otpEventPublisher.publishOtpGenerated(email, otp);
        log.info("=== OTP initiated for {}", email);
    }

    @Override
    public void verifyOtp(String email, String otp) {
        otpValidator.validate(email, otp);
    }
}
