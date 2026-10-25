package com.daniellaera.authservice.domain.model;

/** No account for the email (answered 400 with the message, which differs by use case). */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String message) {
        super(message);
    }
}
