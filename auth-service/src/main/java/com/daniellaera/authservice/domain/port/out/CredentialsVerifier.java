package com.daniellaera.authservice.domain.port.out;

/** Checks an email and password against the stored account; an InvalidCredentialsException when they do not match. */
public interface CredentialsVerifier {

    void verify(String email, String rawPassword);
}
