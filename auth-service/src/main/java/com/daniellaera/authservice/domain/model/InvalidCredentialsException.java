package com.daniellaera.authservice.domain.model;

/**
 * Email and password do not match an account (answered 400 with the message). The message is the credential check's,
 * "Bad credentials" for an unknown user as for a wrong password.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message, Throwable cause) {
        super(message, cause);
    }
}
