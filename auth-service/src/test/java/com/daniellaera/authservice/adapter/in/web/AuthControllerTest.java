package com.daniellaera.authservice.adapter.in.web;

import com.daniellaera.authservice.domain.model.AuthTokens;
import com.daniellaera.authservice.domain.port.in.AccountQuery;
import com.daniellaera.authservice.domain.port.in.LoginUseCase;
import com.daniellaera.authservice.domain.port.in.RefreshTokensUseCase;
import com.daniellaera.authservice.domain.port.in.RegisterUseCase;
import com.daniellaera.authservice.domain.port.in.UpdateProfileUseCase;
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
    private RegisterUseCase registerUseCase;

    @Mock
    private LoginUseCase loginUseCase;

    @Mock
    private RefreshTokensUseCase refreshTokensUseCase;

    @Mock
    private AccountQuery accountQuery;

    @Mock
    private UpdateProfileUseCase updateProfileUseCase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController).build();
    }

    @Test
    void register_shouldReturn200AndToken() throws Exception {
        when(registerUseCase.register("john@test.com", "password123"))
                .thenReturn(new AuthTokens("mocked-token", "mocked-refresh-token"));

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
    void login_shouldReturn200AndRequiresOtp() throws Exception {
        when(loginUseCase.login("john@test.com", "password123")).thenReturn("john@test.com");

        mockMvc.perform(post("/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "john@test.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresOtp").value(true))
                .andExpect(jsonPath("$.email").value("john@test.com"));
    }

    @Test
    void verifyOtp_shouldReturn200AndToken() throws Exception {
        when(loginUseCase.verifyOtp("john@test.com", "123456"))
                .thenReturn(new AuthTokens("login-token", "login-refresh-token"));

        mockMvc.perform(post("/auth/verify-otp")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "john@test.com",
                                  "otp": "123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("login-token"));
    }

    @Test
    void refresh_shouldReturn200AndNewToken() throws Exception {
        when(refreshTokensUseCase.refresh("old-refresh-token")).thenReturn(new AuthTokens("new-token", "new-refresh-token"));

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