package com.daniellaera.authservice.controller;

import com.daniellaera.authservice.audit.AuditPublisher;
import com.daniellaera.authservice.dto.AuthResponse;
import com.daniellaera.authservice.dto.LoginRequest;
import com.daniellaera.authservice.dto.RegisterRequest;
import com.daniellaera.authservice.model.RefreshToken;
import com.daniellaera.authservice.model.User;
import com.daniellaera.authservice.enums.Role;
import com.daniellaera.authservice.service.AuthService;
import com.daniellaera.authservice.service.RefreshTokenService;
import com.daniellaera.authservice.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @InjectMocks
    private AuthController authController;

    @Mock
    private AuthService authService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private AuditPublisher auditPublisher;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController).build();
    }

    @Test
    void register_shouldReturn200AndToken() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(new AuthResponse("mocked-token", "mocked-refresh-token"));

        mockMvc.perform(post("/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "john@test.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mocked-token"));
    }

    @Test
    void login_shouldReturn200AndToken() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenReturn(new AuthResponse("login-token", "login-refresh-token"));

        mockMvc.perform(post("/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "john@test.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("login-token"));
    }

    @Test
    void refresh_shouldReturn200AndNewToken() throws Exception {
        User user = User.builder().email("john@test.com").role(Role.USER).build();
        RefreshToken existingToken = new RefreshToken();
        existingToken.setToken("old-refresh-token");
        existingToken.setUser(user);

        RefreshToken newToken = new RefreshToken();
        newToken.setToken("new-refresh-token");
        newToken.setUser(user);

        when(refreshTokenService.validateRefreshToken("old-refresh-token")).thenReturn(existingToken);
        when(refreshTokenService.createRefreshToken("john@test.com")).thenReturn(newToken);
        when(jwtUtil.generateToken("john@test.com", "USER")).thenReturn("new-token");

        mockMvc.perform(post("/auth/refresh")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "old-refresh-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("new-token"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"));
    }

    @Test
    void logout_shouldReturn204() throws Exception {
        mockMvc.perform(post("/auth/logout")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "some-token"
                                }
                                """))
                .andExpect(status().isNoContent());
    }
}