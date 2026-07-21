package com.daniellaera.authservice.service;

import com.daniellaera.authservice.audit.AuditPublisher;
import com.daniellaera.authservice.dto.AuthResponse;
import com.daniellaera.authservice.dto.LoginRequest;
import com.daniellaera.authservice.dto.OtpInitiatedResponse;
import com.daniellaera.authservice.dto.OtpRequest;
import com.daniellaera.authservice.dto.ProfileRequest;
import com.daniellaera.authservice.dto.ProfileResponse;
import com.daniellaera.authservice.dto.RegisterRequest;
import com.daniellaera.authservice.enums.Role;
import com.daniellaera.authservice.model.RefreshToken;
import com.daniellaera.authservice.model.User;
import com.daniellaera.authservice.otp.OtpService;
import com.daniellaera.authservice.repository.UserRepository;
import com.daniellaera.authservice.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultAuthService implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;
    private final AuditPublisher auditPublisher;
    private final OtpService otpService;

    @Override
    public AuthResponse register(RegisterRequest request) {
        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(Role.USER)
                .createdAt(LocalDateTime.now())
                .build();
        userRepository.save(user);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getEmail());
        auditPublisher.publish("USER_REGISTER", user.getEmail(), "USER", user.getEmail(), user.getEmail());
        return new AuthResponse(generateTokenForUser(user), refreshToken.getToken());
    }

    @Override
    public OtpInitiatedResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new RuntimeException("User not found"));

        otpService.initiateOtp(user.getEmail());
        auditPublisher.publish("USER_LOGIN_INITIATED", user.getEmail(), "USER", user.getEmail(), user.getEmail());

        return new OtpInitiatedResponse(true, user.getEmail(), "OTP sent to your email address");
    }

    @Override
    public AuthResponse verifyOtpAndLogin(OtpRequest request) {
        otpService.verifyOtp(request.email(), request.otp());

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getEmail());
        auditPublisher.publish("USER_LOGIN", user.getEmail(), "USER", user.getEmail(), user.getEmail());

        return new AuthResponse(generateTokenForUser(user), refreshToken.getToken());
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
        auditPublisher.publish("USER_PROFILE_UPDATED", email, "USER", email, request);
        return toProfileResponse(saved);
    }

    @Override
    public boolean isEmailAvailable(String email) {
        return !userRepository.existsByEmail(email);
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