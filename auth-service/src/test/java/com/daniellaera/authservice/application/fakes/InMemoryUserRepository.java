package com.daniellaera.authservice.application.fakes;

import com.daniellaera.authservice.domain.model.EmailAlreadyRegisteredException;
import com.daniellaera.authservice.domain.model.User;
import com.daniellaera.authservice.domain.port.out.UserRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryUserRepository implements UserRepository {

    private final Map<String, User> users = new LinkedHashMap<>();
    private long nextId = 1;

    public User given(User user) {
        return save(user);
    }

    public void remove(String email) {
        users.remove(email);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return Optional.ofNullable(users.get(email));
    }

    @Override
    public boolean existsByEmail(String email) {
        return users.containsKey(email);
    }

    @Override
    public User save(User user) {
        if (user.id() == null) {
            if (users.containsKey(user.email())) {
                throw new EmailAlreadyRegisteredException(null);
            }
            User stored = new User(nextId++, user.email(), user.passwordHash(), user.firstName(), user.lastName(),
                    user.role(), user.createdAt());
            users.put(user.email(), stored);
            return stored;
        }
        users.put(user.email(), user);
        return user;
    }
}
