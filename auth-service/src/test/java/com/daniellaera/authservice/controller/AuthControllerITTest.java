package com.daniellaera.authservice.controller;

import com.daniellaera.authservice.TestcontainersConfiguration;
import com.daniellaera.authservice.domain.port.out.OtpRepository;
import com.daniellaera.authservice.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class AuthControllerITTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OtpRepository otpRepository;

    @MockitoBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresOtp").value(true));

        String otp = otpRepository.find(email).orElseThrow();

        MvcResult verifyResult = mockMvc.perform(post("/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "otp": "%s"
                                }
                                """.formatted(email, otp)))
                .andExpect(status().isOk())
                .andReturn();

        return verifyResult.getResponse().getContentAsString();
    }

    @Test
    void register_shouldCreateUserAndReturnToken() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "john@test.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        assertThat(userRepository.findByEmail("john@test.com")).isPresent();
    }

    @Test
    void login_shouldRequireOtp_forExistingUser() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "jane@test.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "jane@test.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresOtp").value(true))
                .andExpect(jsonPath("$.email").value("jane@test.com"))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void verifyOtp_shouldReturnToken_afterValidLogin() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "jane@test.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk());

        String responseBody = loginAndGetToken("jane@test.com", "password123");
        String token = JsonPath.read(responseBody, "$.token");
        assertThat(token).isNotBlank();

        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        assertThat(payload).contains("jane@test.com");
    }

    @Test
    void verifyOtp_shouldReturn401_whenOtpWrong() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "wrongotp@test.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "wrongotp@test.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "wrongotp@test.com",
                                  "otp": "000000"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verifyOtp_shouldReturn401_whenOtpNotFound() throws Exception {
        mockMvc.perform(post("/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "never-logged-in@test.com",
                                  "otp": "123456"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    private void register(String email) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "password": "password123" }
                                """.formatted(email)))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions login(String email) throws Exception {
        return mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "email": "%s", "password": "password123" }
                        """.formatted(email)));
    }

    private org.springframework.test.web.servlet.ResultActions verify(String email, String otp) throws Exception {
        return mockMvc.perform(post("/auth/verify-otp")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "email": "%s", "otp": "%s" }
                        """.formatted(email, otp)));
    }

    @Test
    void verifyOtp_fiveWrongCodes_invalidateTheOtp() throws Exception {
        register("guessing@test.com");
        login("guessing@test.com").andExpect(status().isOk());
        String otp = otpRepository.find("guessing@test.com").orElseThrow();

        for (int i = 1; i <= 4; i++) {
            verify("guessing@test.com", "000000")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Invalid OTP code"));
        }
        verify("guessing@test.com", "000000")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Too many invalid codes — please login again"));

        // the right code no longer works: the OTP is gone
        verify("guessing@test.com", otp)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("OTP expired or not found — please login again"));
        assertThat(otpRepository.find("guessing@test.com")).isEmpty();
    }

    @Test
    void verifyOtp_wrongCodesOfAnEarlierOtp_doNotCountAgainstANewOne() throws Exception {
        register("retrying@test.com");
        login("retrying@test.com").andExpect(status().isOk());
        for (int i = 1; i <= 4; i++) {
            verify("retrying@test.com", "000000").andExpect(status().isUnauthorized());
        }

        login("retrying@test.com").andExpect(status().isOk());
        verify("retrying@test.com", "000000").andExpect(status().isUnauthorized());
        verify("retrying@test.com", otpRepository.find("retrying@test.com").orElseThrow())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void login_aSixthCodeWithin15Minutes_isRefused() throws Exception {
        register("hammering@test.com");
        for (int i = 1; i <= 5; i++) {
            login("hammering@test.com").andExpect(status().isOk());
        }

        login("hammering@test.com")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Too many login attempts — please try again in 15 minutes"));
    }

    @Test
    void login_aWrongPassword_doesNotUseUpTheCodes() throws Exception {
        register("victim@test.com");
        for (int i = 1; i <= 6; i++) {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "email": "victim@test.com", "password": "not-the-password" }
                                    """))
                    .andExpect(status().is4xxClientError());
        }

        login("victim@test.com").andExpect(status().isOk());
    }

    @Test
    void register_shouldIgnoreExtraFields() throws Exception {
        // Jackson does not fail on unknown properties in this config,
        // so legacy firstName/lastName fields are silently dropped.
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "John",
                                  "lastName": "Doe",
                                  "email": "extra@test.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        assertThat(userRepository.findByEmail("extra@test.com"))
                .isPresent()
                .get()
                .satisfies(user -> {
                    assertThat(user.getFirstName()).isNull();
                    assertThat(user.getLastName()).isNull();
                });
    }

    @Test
    void register_shouldReturn400_whenEmailMissing() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").value("Email is required"));
    }

    @Test
    void register_shouldReturn400_whenEmailInvalid() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "not-an-email",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").value("Email must be valid"));
    }

    @Test
    void register_shouldReturn400_whenPasswordTooShort() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "shortpw@test.com",
                                  "password": "123"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value("Password must be at least 6 characters"));
    }

    @Test
    void getProfile_shouldReturn401_withoutAuthentication() throws Exception {
        mockMvc.perform(get("/auth/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getProfile_shouldReturnProfile_withValidAuthentication() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "profile@test.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/auth/profile")
                        .header("X-User-Email", "profile@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("profile@test.com"))
                .andExpect(jsonPath("$.firstName").doesNotExist())
                .andExpect(jsonPath("$.lastName").doesNotExist());
    }

    @Test
    void updateProfile_shouldUpdateNames_withValidAuthentication() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "update@test.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/auth/profile")
                        .header("X-User-Email", "update@test.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "John",
                                  "lastName": "Doe"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.displayName").value("John Doe"));
    }

    @Test
    void verifyOtp_shouldReturnRefreshToken_forExistingUser() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "refresh@test.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk());

        String responseBody = loginAndGetToken("refresh@test.com", "password123");
        String refreshToken = JsonPath.read(responseBody, "$.refreshToken");
        assertThat(refreshToken).isNotBlank();
    }

    @Test
    void refresh_shouldReturnNewTokenAndRefreshToken_whenValid() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "rotate@test.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk());

        String loginResponseBody = loginAndGetToken("rotate@test.com", "password123");
        String refreshToken = JsonPath.read(loginResponseBody, "$.refreshToken");

        MvcResult refreshResult = mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        String newRefreshToken = JsonPath.read(refreshResult.getResponse().getContentAsString(), "$.refreshToken");
        assertThat(newRefreshToken).isNotEqualTo(refreshToken);

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(refreshToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refresh_shouldFail_whenTokenUnknown() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "does-not-exist"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logout_shouldRevokeRefreshToken() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "logout@test.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk());

        String loginResponseBody = loginAndGetToken("logout@test.com", "password123");
        String refreshToken = JsonPath.read(loginResponseBody, "$.refreshToken");

        mockMvc.perform(post("/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(refreshToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "%s"
                                }
                                """.formatted(refreshToken)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateProfile_shouldReturn400_whenFirstNameTooLong() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "longname@test.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk());

        String tooLong = "a".repeat(51);

        mockMvc.perform(put("/auth/profile")
                        .header("X-User-Email", "longname@test.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "%s",
                                  "lastName": "Doe"
                                }
                                """.formatted(tooLong)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.firstName").value("First name too long"));
    }
}