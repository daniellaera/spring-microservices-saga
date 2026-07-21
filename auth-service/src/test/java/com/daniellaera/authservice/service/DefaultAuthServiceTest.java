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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultAuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private AuditPublisher auditPublisher;
    @Mock private OtpService otpService;

    @InjectMocks
    private DefaultAuthService authService;

    @Test
    void register_shouldCreateUserAndReturnToken() {
        RegisterRequest request = new RegisterRequest("john@test.com", "password123");
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(jwtUtil.generateToken("john@test.com", "USER")).thenReturn("mocked-token");
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("mocked-refresh-token");
        when(refreshTokenService.createRefreshToken("john@test.com")).thenReturn(refreshToken);

        AuthResponse response = authService.register(request);

        assertThat(response.token()).isEqualTo("mocked-token");
        assertThat(response.refreshToken()).isEqualTo("mocked-refresh-token");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo("john@test.com");
        assertThat(savedUser.getFirstName()).isNull();
        assertThat(savedUser.getLastName()).isNull();

        verify(jwtUtil, times(1)).generateToken("john@test.com", "USER");
        verify(auditPublisher).publish(eq("USER_REGISTER"), any(), any(), any(), any());
    }

    @Test
    void login_shouldAuthenticateAndInitiateOtp() {
        LoginRequest request = new LoginRequest("admin@test.com", "password");
        User user = User.builder()
                .email("admin@test.com")
                .role(Role.ADMIN)
                .build();
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(user));

        OtpInitiatedResponse response = authService.login(request);

        assertThat(response.requiresOtp()).isTrue();
        assertThat(response.email()).isEqualTo("admin@test.com");
        verify(authenticationManager, times(1)).authenticate(any());
        verify(otpService, times(1)).initiateOtp("admin@test.com");
        verify(auditPublisher).publish(eq("USER_LOGIN_INITIATED"), any(), any(), any(), any());
    }

    @Test
    void verifyOtpAndLogin_shouldValidateOtpAndReturnToken() {
        OtpRequest request = new OtpRequest("admin@test.com", "123456");
        User user = User.builder()
                .email("admin@test.com")
                .role(Role.ADMIN)
                .build();
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(user));
        when(jwtUtil.generateToken("admin@test.com", "ADMIN")).thenReturn("admin-token");
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("admin-refresh-token");
        when(refreshTokenService.createRefreshToken("admin@test.com")).thenReturn(refreshToken);

        AuthResponse response = authService.verifyOtpAndLogin(request);

        assertThat(response.token()).isEqualTo("admin-token");
        assertThat(response.refreshToken()).isEqualTo("admin-refresh-token");
        verify(otpService, times(1)).verifyOtp("admin@test.com", "123456");
        verify(auditPublisher).publish(eq("USER_LOGIN"), any(), any(), any(), any());
    }

    @Test
    void getProfile_shouldReturnProfileResponse() {
        User user = User.builder()
                .email("john@test.com")
                .firstName("John")
                .lastName("Doe")
                .role(Role.USER)
                .build();
        when(userRepository.findByEmail("john@test.com")).thenReturn(Optional.of(user));

        ProfileResponse response = authService.getProfile("john@test.com");

        assertThat(response.email()).isEqualTo("john@test.com");
        assertThat(response.firstName()).isEqualTo("John");
        assertThat(response.lastName()).isEqualTo("Doe");
        assertThat(response.displayName()).isEqualTo("John Doe");
        assertThat(response.role()).isEqualTo("USER");
    }

    @Test
    void getProfile_shouldFallBackToEmailPrefix_whenNamesAreNull() {
        User user = User.builder()
                .email("john@test.com")
                .role(Role.USER)
                .build();
        when(userRepository.findByEmail("john@test.com")).thenReturn(Optional.of(user));

        ProfileResponse response = authService.getProfile("john@test.com");

        assertThat(response.firstName()).isNull();
        assertThat(response.lastName()).isNull();
        assertThat(response.displayName()).isEqualTo("john");
    }

    @Test
    void getProfile_shouldThrow_whenUserNotFound() {
        when(userRepository.findByEmail("missing@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.getProfile("missing@test.com"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("missing@test.com");
    }

    @Test
    void updateProfile_shouldUpdateFirstAndLastName() {
        User user = User.builder()
                .email("john@test.com")
                .role(Role.USER)
                .build();
        when(userRepository.findByEmail("john@test.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProfileResponse response = authService.updateProfile(
                "john@test.com", new ProfileRequest("John", "Doe"));

        assertThat(response.firstName()).isEqualTo("John");
        assertThat(response.lastName()).isEqualTo("Doe");
        assertThat(response.displayName()).isEqualTo("John Doe");
    }

    @Test
    void updateProfile_shouldSupportPartialUpdate() {
        User user = User.builder()
                .email("john@test.com")
                .firstName("Old")
                .lastName("Name")
                .role(Role.USER)
                .build();
        when(userRepository.findByEmail("john@test.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProfileResponse response = authService.updateProfile(
                "john@test.com", new ProfileRequest("New", null));

        assertThat(response.firstName()).isEqualTo("New");
        assertThat(response.lastName()).isEqualTo("Name");
    }

    @Test
    void updateProfile_shouldThrow_whenUserNotFound() {
        when(userRepository.findByEmail("missing@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                authService.updateProfile("missing@test.com", new ProfileRequest("John", "Doe")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("missing@test.com");
    }
}