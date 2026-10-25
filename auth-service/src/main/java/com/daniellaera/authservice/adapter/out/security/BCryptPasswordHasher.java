package com.daniellaera.authservice.adapter.out.security;

import com.daniellaera.authservice.domain.port.out.PasswordHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** The configured PasswordEncoder (BCrypt), the one the credential check matches against. */
@Component
@RequiredArgsConstructor
public class BCryptPasswordHasher implements PasswordHasher {

    private final PasswordEncoder passwordEncoder;

    @Override
    public String hash(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }
}
