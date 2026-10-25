package com.daniellaera.authservice.domain.model;

import java.time.LocalDateTime;

/** An account: the email is the login, the password only ever stored hashed. id is null until it is stored. */
public record User(Long id, String email, String passwordHash, String firstName, String lastName, Role role,
                   LocalDateTime createdAt) {

    public static User register(String email, String passwordHash, LocalDateTime now) {
        return new User(null, email, passwordHash, null, null, Role.USER, now);
    }

    // a name that is not given (null) is left as it is
    public User withNames(String newFirstName, String newLastName) {
        return new User(id, email, passwordHash, newFirstName != null ? newFirstName : firstName,
                newLastName != null ? newLastName : lastName, role, createdAt);
    }

    // "First Last", "First", or the part of the email before the @
    public String displayName() {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        } else if (firstName != null) {
            return firstName;
        }
        return email.split("@")[0];
    }
}
