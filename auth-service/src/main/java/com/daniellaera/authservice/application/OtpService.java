package com.daniellaera.authservice.application;

import com.daniellaera.authservice.domain.model.AuthenticationFailedException;
import com.daniellaera.authservice.domain.model.OtpCode;
import com.daniellaera.authservice.domain.model.OtpLimitExceededException;
import com.daniellaera.authservice.domain.port.out.OtpAttempts;
import com.daniellaera.authservice.domain.port.out.OtpEventPublisher;
import com.daniellaera.authservice.domain.port.out.OtpGenerator;
import com.daniellaera.authservice.domain.port.out.OtpRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * The one-time codes of the second login step. A code lives OTP_TTL and is good for one successful verification.
 * After MAX_WRONG_CODES wrong codes it is deleted, so guessing it needs a new login (with the password), and at most
 * MAX_CODES_ISSUED codes (so MAX_CODES_ISSUED × 5 guesses) are issued per email in ISSUE_WINDOW.
 */
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    static final Duration OTP_TTL = Duration.ofMinutes(5);
    static final int MAX_CODES_ISSUED = 5;
    static final Duration ISSUE_WINDOW = Duration.ofMinutes(15);
    static final int MAX_WRONG_CODES = 5;

    private final OtpGenerator otpGenerator;
    private final OtpRepository otpRepository;
    private final OtpEventPublisher otpEventPublisher;
    private final OtpAttempts otpAttempts;

    public OtpService(OtpGenerator otpGenerator, OtpRepository otpRepository, OtpEventPublisher otpEventPublisher,
                      OtpAttempts otpAttempts) {
        this.otpGenerator = otpGenerator;
        this.otpRepository = otpRepository;
        this.otpEventPublisher = otpEventPublisher;
        this.otpAttempts = otpAttempts;
    }

    // Called after the password check, so only someone who knows the password can use up the email's codes
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

    public void verifyOtp(String email, String otp) {
        String stored = otpRepository.find(email)
                .orElseThrow(() -> new AuthenticationFailedException("OTP expired or not found — please login again"));

        if (!stored.equals(otp)) {
            long wrongCodes = otpAttempts.recordFailure(email, OTP_TTL);
            if (wrongCodes >= MAX_WRONG_CODES) {
                otpRepository.delete(email);
                otpAttempts.resetFailures(email);
                log.warn("=== OTP for {} invalidated after {} wrong codes", email, wrongCodes);
                throw new OtpLimitExceededException(OtpLimitExceededException.TOO_MANY_WRONG_CODES,
                        "Too many invalid codes — please login again");
            }
            throw new AuthenticationFailedException("Invalid OTP code");
        }

        otpRepository.delete(email);
        otpAttempts.resetFailures(email);
        log.info("=== OTP validated and consumed for {}", email);
    }
}
