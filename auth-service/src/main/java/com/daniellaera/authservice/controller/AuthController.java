package com.daniellaera.authservice.controller;

import com.daniellaera.authservice.dto.AuthResponse;
import com.daniellaera.authservice.dto.LoginRequest;
import com.daniellaera.authservice.dto.ProfileRequest;
import com.daniellaera.authservice.dto.ProfileResponse;
import com.daniellaera.authservice.dto.RefreshTokenRequest;
import com.daniellaera.authservice.dto.RegisterRequest;
import com.daniellaera.authservice.model.RefreshToken;
import com.daniellaera.authservice.service.AuthService;
import com.daniellaera.authservice.service.RefreshTokenService;
import com.daniellaera.authservice.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final JwtUtil jwtUtil;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        RefreshToken refreshToken = refreshTokenService.validateRefreshToken(request.refreshToken());

        String userEmail = refreshToken.getUser().getEmail();
        String role = refreshToken.getUser().getRole().name();

        refreshTokenService.revokeRefreshToken(request.refreshToken());
        RefreshToken newRefreshToken = refreshTokenService.createRefreshToken(userEmail);

        String newJwt = jwtUtil.generateToken(userEmail, role);

        return ResponseEntity.ok(new AuthResponse(newJwt, newRefreshToken.getToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        refreshTokenService.revokeRefreshToken(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/profile")
    public ResponseEntity<ProfileResponse> getProfile(
            @RequestHeader(value = "X-User-Email", required = false) String email) {
        requireEmail(email);
        return ResponseEntity.ok(authService.getProfile(email));
    }

    @PutMapping("/profile")
    public ResponseEntity<ProfileResponse> updateProfile(
            @RequestHeader(value = "X-User-Email", required = false) String email,
            @Valid @RequestBody ProfileRequest request) {
        requireEmail(email);
        return ResponseEntity.ok(authService.updateProfile(email, request));
    }

    private void requireEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing authentication");
        }
    }
}