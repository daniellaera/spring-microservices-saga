package com.daniellaera.authservice.otp;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 429: an OTP limit was reached. {@link #auditReason()} names which one, for the USER_LOGIN_FAILED audit. */
public class OtpLimitExceededException extends ResponseStatusException {

    public static final String TOO_MANY_WRONG_CODES = "OTP_TOO_MANY_WRONG_CODES";
    public static final String TOO_MANY_CODES_ISSUED = "OTP_TOO_MANY_CODES_ISSUED";

    private final String auditReason;

    public OtpLimitExceededException(String auditReason, String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, message);
        this.auditReason = auditReason;
    }

    public String auditReason() {
        return auditReason;
    }
}
