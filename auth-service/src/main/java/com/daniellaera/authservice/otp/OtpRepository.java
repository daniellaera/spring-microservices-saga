package com.daniellaera.authservice.otp;

import java.time.Duration;
import java.util.Optional;

public interface OtpRepository {
    void save(String email, OtpCode otp, Duration ttl);
    Optional<String> find(String email);
    void delete(String email);
}
