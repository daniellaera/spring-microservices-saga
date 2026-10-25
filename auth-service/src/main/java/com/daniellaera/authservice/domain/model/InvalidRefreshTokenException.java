package com.daniellaera.authservice.domain.model;

/** A refresh token that is unknown, revoked or expired (answered 400 with the message). */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
