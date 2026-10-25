package com.daniellaera.authservice.domain.model;

/** An OTP limit was reached (answered 429). {@link #auditReason()} names which one, for the USER_LOGIN_FAILED audit. */
public class OtpLimitExceededException extends RuntimeException {

    public static final String TOO_MANY_WRONG_CODES = "OTP_TOO_MANY_WRONG_CODES";
    public static final String TOO_MANY_CODES_ISSUED = "OTP_TOO_MANY_CODES_ISSUED";

    private final String auditReason;

    public OtpLimitExceededException(String auditReason, String message) {
        super(message);
        this.auditReason = auditReason;
    }

    public String auditReason() {
        return auditReason;
    }
}
