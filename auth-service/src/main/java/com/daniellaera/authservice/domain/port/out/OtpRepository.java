package com.daniellaera.authservice.domain.port.out;

import com.daniellaera.authservice.domain.model.OtpCode;
import java.time.Duration;
import java.util.Optional;

public interface OtpRepository {
    void save(String email, OtpCode otp, Duration ttl);
    Optional<String> find(String email);
    void delete(String email);
}
