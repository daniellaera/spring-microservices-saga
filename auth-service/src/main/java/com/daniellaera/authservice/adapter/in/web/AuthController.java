package com.daniellaera.authservice.adapter.in.web;

import com.daniellaera.authservice.adapter.in.web.dto.AuthResponse;
import com.daniellaera.authservice.adapter.in.web.dto.LoginRequest;
import com.daniellaera.authservice.adapter.in.web.dto.OtpInitiatedResponse;
import com.daniellaera.authservice.adapter.in.web.dto.OtpRequest;
import com.daniellaera.authservice.adapter.in.web.dto.ProfileRequest;
import com.daniellaera.authservice.adapter.in.web.dto.ProfileResponse;
import com.daniellaera.authservice.adapter.in.web.dto.RefreshTokenRequest;
import com.daniellaera.authservice.adapter.in.web.dto.RegisterRequest;
import com.daniellaera.authservice.domain.model.AuthTokens;
import com.daniellaera.authservice.domain.model.User;
import com.daniellaera.authservice.domain.port.in.AccountQuery;
import com.daniellaera.authservice.domain.port.in.LoginUseCase;
import com.daniellaera.authservice.domain.port.in.RefreshTokensUseCase;
import com.daniellaera.authservice.domain.port.in.RegisterUseCase;
import com.daniellaera.authservice.domain.port.in.UpdateProfileUseCase;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final RegisterUseCase registerUseCase;
    private final LoginUseCase loginUseCase;
    private final RefreshTokensUseCase refreshTokensUseCase;
    private final AccountQuery accountQuery;
    private final UpdateProfileUseCase updateProfileUseCase;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(toResponse(registerUseCase.register(request.email(), request.password())));
    }

    @PostMapping("/login")
    public ResponseEntity<OtpInitiatedResponse> login(@Valid @RequestBody LoginRequest request) {
        String email = loginUseCase.login(request.email(), request.password());
        return ResponseEntity.ok(new OtpInitiatedResponse(true, email, "OTP sent to your email address"));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<AuthResponse> verifyOtp(@Valid @RequestBody OtpRequest request) {
        return ResponseEntity.ok(toResponse(loginUseCase.verifyOtp(request.email(), request.otp())));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(toResponse(refreshTokensUseCase.refresh(request.refreshToken())));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        refreshTokensUseCase.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/check-email")
    public ResponseEntity<Map<String, Boolean>> checkEmail(@RequestParam(name = "email") String email) {
        boolean available = accountQuery.isEmailAvailable(email);
        return ResponseEntity.ok(Map.of("available", available));
    }

    @GetMapping("/profile")
    public ResponseEntity<ProfileResponse> getProfile(
            @RequestHeader(value = "X-User-Email", required = false) String email) {
        requireEmail(email);
        return ResponseEntity.ok(toResponse(accountQuery.getProfile(email)));
    }

    @PutMapping("/profile")
    public ResponseEntity<ProfileResponse> updateProfile(
            @RequestHeader(value = "X-User-Email", required = false) String email,
            @Valid @RequestBody ProfileRequest request) {
        requireEmail(email);
        return ResponseEntity.ok(toResponse(
                updateProfileUseCase.updateProfile(email, request.firstName(), request.lastName())));
    }

    private void requireEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing authentication");
        }
    }

    private static AuthResponse toResponse(AuthTokens tokens) {
        return new AuthResponse(tokens.accessToken(), tokens.refreshToken());
    }

    private static ProfileResponse toResponse(User user) {
        return new ProfileResponse(user.email(), user.firstName(), user.lastName(), user.displayName(),
                user.role().name());
    }
}
