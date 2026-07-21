package com.daniellaera.authservice.service;

import com.daniellaera.authservice.dto.AuthResponse;
import com.daniellaera.authservice.dto.LoginRequest;
import com.daniellaera.authservice.dto.OtpInitiatedResponse;
import com.daniellaera.authservice.dto.OtpRequest;
import com.daniellaera.authservice.dto.ProfileRequest;
import com.daniellaera.authservice.dto.ProfileResponse;
import com.daniellaera.authservice.dto.RegisterRequest;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    OtpInitiatedResponse login(LoginRequest request);
    AuthResponse verifyOtpAndLogin(OtpRequest request);
    ProfileResponse getProfile(String email);
    ProfileResponse updateProfile(String email, ProfileRequest request);
    boolean isEmailAvailable(String email);
}
