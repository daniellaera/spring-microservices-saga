package com.daniellaera.authservice.service;

import com.daniellaera.authservice.dto.AuthResponse;
import com.daniellaera.authservice.dto.LoginRequest;
import com.daniellaera.authservice.dto.RegisterRequest;
import com.daniellaera.authservice.enums.Role;
import com.daniellaera.authservice.model.User;
import com.daniellaera.authservice.repository.UserRepository;
import com.daniellaera.authservice.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultAuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private AuthenticationManager authenticationManager;

    @InjectMocks
    private DefaultAuthService authService;

    @Test
    void register_shouldCreateUserAndReturnToken() {
        RegisterRequest request = new RegisterRequest("John", "Doe", "john@test.com", "password123");
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(jwtUtil.generateToken("john@test.com", "USER")).thenReturn("mocked-token");

        AuthResponse response = authService.register(request);

        assertThat(response.token()).isEqualTo("mocked-token");
        verify(userRepository, times(1)).save(any(User.class));
        verify(jwtUtil, times(1)).generateToken("john@test.com", "USER");
    }

    @Test
    void login_shouldAuthenticateAndReturnToken() {
        LoginRequest request = new LoginRequest("admin@test.com", "password");
        User user = User.builder()
                .email("admin@test.com")
                .role(Role.ADMIN)
                .build();
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken("admin@test.com", "ADMIN")).thenReturn("admin-token");

        AuthResponse response = authService.login(request);

        assertThat(response.token()).isEqualTo("admin-token");
        verify(authenticationManager, times(1)).authenticate(any());
        verify(jwtUtil, times(1)).generateToken("admin@test.com", "ADMIN");
    }
}