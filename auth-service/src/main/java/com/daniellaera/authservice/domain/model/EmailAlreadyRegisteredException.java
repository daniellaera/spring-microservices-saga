package com.daniellaera.authservice.domain.model;

/** An account with this email exists already (answered 409). */
public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException(Throwable cause) {
        super("An account with this email already exists", cause);
    }
}
