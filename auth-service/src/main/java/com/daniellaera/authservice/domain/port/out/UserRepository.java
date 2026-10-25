package com.daniellaera.authservice.domain.port.out;

import com.daniellaera.authservice.domain.model.User;

import java.util.Optional;

public interface UserRepository {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    // a new user (no id) is inserted, an EmailAlreadyRegisteredException if the email is taken; an existing one updated
    User save(User user);
}
