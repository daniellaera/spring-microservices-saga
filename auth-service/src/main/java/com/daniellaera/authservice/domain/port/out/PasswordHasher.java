package com.daniellaera.authservice.domain.port.out;

public interface PasswordHasher {

    String hash(String rawPassword);
}
