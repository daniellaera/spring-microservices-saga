package com.daniellaera.authservice.service;

import com.daniellaera.authservice.dto.AuthResponse;
import com.daniellaera.authservice.dto.LoginRequest;
import com.daniellaera.authservice.dto.ProfileRequest;
import com.daniellaera.authservice.dto.ProfileResponse;
import com.daniellaera.authservice.dto.RegisterRequest;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);
    ProfileResponse getProfile(String email);
    ProfileResponse updateProfile(String email, ProfileRequest request);
}
