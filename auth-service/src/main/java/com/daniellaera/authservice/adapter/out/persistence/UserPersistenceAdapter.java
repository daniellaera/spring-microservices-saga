package com.daniellaera.authservice.adapter.out.persistence;

import com.daniellaera.authservice.domain.model.EmailAlreadyRegisteredException;
import com.daniellaera.authservice.domain.model.User;
import com.daniellaera.authservice.domain.port.out.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UserPersistenceAdapter implements UserRepository {

    private final UserJpaRepository userJpaRepository;

    @Override
    public Optional<User> findByEmail(String email) {
        return userJpaRepository.findByEmail(email).map(AuthJpaMapper::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userJpaRepository.existsByEmail(email);
    }

    // a new user is inserted at once (IDENTITY), so the unique email is checked here
    @Override
    public User save(User user) {
        if (user.id() == null) {
            try {
                return AuthJpaMapper.toDomain(userJpaRepository.save(AuthJpaMapper.toEntity(user)));
            } catch (DataIntegrityViolationException e) {
                throw new EmailAlreadyRegisteredException(e);
            }
        }
        return AuthJpaMapper.toDomain(userJpaRepository.save(AuthJpaMapper.toEntity(user)));
    }
}
