package com.daniellaera.authservice.domain.port.out;

import java.time.Duration;

/** Counters behind the OTP limits: wrong codes for the current OTP, and OTPs issued per email. */
public interface OtpAttempts {
    /** Counts one more wrong code for the email's current OTP; the count expires with it. */
    long recordFailure(String email, Duration ttl);

    /** Counts one more OTP issued for the email in the current window, which starts with the first one. */
    long recordIssued(String email, Duration window);

    /** Forgets the wrong codes of the email's current OTP. */
    void resetFailures(String email);
}
