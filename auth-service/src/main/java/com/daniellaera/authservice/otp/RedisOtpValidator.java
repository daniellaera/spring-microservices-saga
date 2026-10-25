package com.daniellaera.authservice.otp;

import com.daniellaera.authservice.domain.port.out.OtpRepository;
import com.daniellaera.authservice.domain.port.out.OtpAttempts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * A code is good for one successful verification. After {@link #MAX_WRONG_CODES} wrong codes the OTP is
 * deleted, so guessing it needs a new login (with the password), and logins are limited per email too
 * (see {@link OtpServiceImpl}).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisOtpValidator implements OtpValidator {

    static final int MAX_WRONG_CODES = 5;

    private final OtpRepository otpRepository;
    private final OtpAttempts otpAttempts;

    @Override
    public void validate(String email, String otp) {
        String stored = otpRepository.find(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "OTP expired or not found — please login again"));

        if (!stored.equals(otp)) {
            long wrongCodes = otpAttempts.recordFailure(email, OtpServiceImpl.OTP_TTL);
            if (wrongCodes >= MAX_WRONG_CODES) {
                otpRepository.delete(email);
                otpAttempts.resetFailures(email);
                log.warn("=== OTP for {} invalidated after {} wrong codes", email, wrongCodes);
                throw new OtpLimitExceededException(OtpLimitExceededException.TOO_MANY_WRONG_CODES,
                        "Too many invalid codes — please login again");
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid OTP code");
        }

        otpRepository.delete(email);
        otpAttempts.resetFailures(email);
        log.info("=== OTP validated and consumed for {}", email);
    }
}
