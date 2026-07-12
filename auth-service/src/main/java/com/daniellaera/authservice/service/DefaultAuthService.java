package com.daniellaera.authservice.service;

import com.daniellaera.authservice.dto.AuthResponse;
import com.daniellaera.authservice.dto.LoginRequest;
import com.daniellaera.authservice.dto.ProfileRequest;
import com.daniellaera.authservice.dto.ProfileResponse;
import com.daniellaera.authservice.dto.RegisterRequest;
import com.daniellaera.authservice.enums.Role;
import com.daniellaera.authservice.model.User;
import com.daniellaera.authservice.repository.UserRepository;
import com.daniellaera.authservice.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultAuthService implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    @Override
    public AuthResponse register(RegisterRequest request) {
        User user = User.builder()
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

    @Override
    public ProfileResponse getProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));
        return toProfileResponse(user);
    }

    @Override
    public ProfileResponse updateProfile(String email, ProfileRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found: " + email));

        if (request.firstName() != null) {
            user.setFirstName(request.firstName());
        }
        if (request.lastName() != null) {
            user.setLastName(request.lastName());
        }

        User saved = userRepository.save(user);
        log.info("=== Profile updated for {}", email);
        return toProfileResponse(saved);
    }

    private ProfileResponse toProfileResponse(User user) {
        String displayName;
        if (user.getFirstName() != null && user.getLastName() != null) {
            displayName = user.getFirstName() + " " + user.getLastName();
        } else if (user.getFirstName() != null) {
            displayName = user.getFirstName();
        } else {
            displayName = user.getEmail().split("@")[0];
        }

        return new ProfileResponse(
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                displayName,
                user.getRole().name()
        );
    }

    private String generateTokenForUser(User user) {
        return jwtUtil.generateToken(user.getEmail(), user.getRole().name());
    }
}