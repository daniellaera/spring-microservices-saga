package com.daniellaera.authservice.service;

import com.daniellaera.authservice.dto.AuthResponse;
import com.daniellaera.authservice.dto.LoginRequest;
import com.daniellaera.authservice.dto.RegisterRequest;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
}
