package com.daniellaera.authservice.adapter.out.security;

import com.daniellaera.authservice.domain.model.InvalidCredentialsException;
import com.daniellaera.authservice.domain.port.out.CredentialsVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

/**
 * Spring Security's AuthenticationManager (DaoAuthenticationProvider, BCrypt). Its message is kept: "Bad credentials"
 * for a wrong password and, since it hides unknown users, for an unknown email too.
 */
@Component
@RequiredArgsConstructor
public class SpringSecurityCredentialsVerifier implements CredentialsVerifier {

    private final AuthenticationManager authenticationManager;

    @Override
    public void verify(String email, String rawPassword) {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, rawPassword));
        } catch (AuthenticationException e) {
            throw new InvalidCredentialsException(e.getMessage(), e);
        }
    }
}
