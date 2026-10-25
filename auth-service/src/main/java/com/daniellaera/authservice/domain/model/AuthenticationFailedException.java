package com.daniellaera.authservice.domain.model;

/** The caller could not be authenticated (answered 401): an OTP that is wrong, expired or used, an unknown user. */
public class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException(String message) {
        super(message);
    }
}
