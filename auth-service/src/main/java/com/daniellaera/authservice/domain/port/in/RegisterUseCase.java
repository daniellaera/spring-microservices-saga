package com.daniellaera.authservice.domain.port.in;

import com.daniellaera.authservice.domain.model.AuthTokens;

public interface RegisterUseCase {

    AuthTokens register(String email, String rawPassword);
}
