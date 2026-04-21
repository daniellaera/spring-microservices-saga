package com.daniellaera.authservice.service;

import com.daniellaera.authservice.dto.AuthResponse;
import com.daniellaera.authservice.dto.LoginRequest;
import com.daniellaera.authservice.dto.RegisterRequest;
import com.daniellaera.authservice.enums.Role;
import com.daniellaera.authservice.model.User;
import com.daniellaera.authservice.repository.UserRepository;
import com.daniellaera.authservice.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DefaultAuthService implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    @Override
    public AuthResponse register(RegisterRequest request) {
        User user = User.builder()
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(Role.USER)
                .createdAt(LocalDateTime.now())
                .build();
        userRepository.save(user);
        return new AuthResponse(generateTokenForUser(user));
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return new AuthResponse(generateTokenForUser(user));
    }

    private String generateTokenForUser(User user) {
        return jwtUtil.generateToken(user.getEmail(), user.getRole().name());
    }
}