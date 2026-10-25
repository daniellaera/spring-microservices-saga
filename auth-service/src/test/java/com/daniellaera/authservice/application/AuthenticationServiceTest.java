package com.daniellaera.authservice.application;

import com.daniellaera.authservice.application.fakes.InMemoryRefreshTokenRepository;
import com.daniellaera.authservice.application.fakes.InMemoryUserRepository;
import com.daniellaera.authservice.application.fakes.OtpFakes.FixedOtpGenerator;
import com.daniellaera.authservice.application.fakes.OtpFakes.InMemoryOtpAttempts;
import com.daniellaera.authservice.application.fakes.OtpFakes.InMemoryOtpRepository;
import com.daniellaera.authservice.application.fakes.OtpFakes.RecordingOtpEventPublisher;
import com.daniellaera.authservice.application.fakes.RecordingAuditTrail;
import com.daniellaera.authservice.domain.model.AuthTokens;
import com.daniellaera.authservice.domain.model.AuthenticationFailedException;
import com.daniellaera.authservice.domain.model.InvalidCredentialsException;
import com.daniellaera.authservice.domain.model.InvalidRefreshTokenException;
import com.daniellaera.authservice.domain.model.OtpLimitExceededException;
import com.daniellaera.authservice.domain.model.Role;
import com.daniellaera.authservice.domain.model.User;
import com.daniellaera.authservice.domain.model.UserNotFoundException;
import com.daniellaera.authservice.domain.port.out.CredentialsVerifier;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class AuthenticationServiceTest {

    private static final String EMAIL = "john@test.com";

    private final InMemoryUserRepository users = new InMemoryUserRepository();
    private final InMemoryRefreshTokenRepository tokens = new InMemoryRefreshTokenRepository();
    private final RecordingAuditTrail audit = new RecordingAuditTrail();
    private final InMemoryOtpRepository otpCodes = new InMemoryOtpRepository();
    private final InMemoryOtpAttempts otpAttempts = new InMemoryOtpAttempts();
    private final Map<String, String> passwords = new HashMap<>();

    // the credential check against the passwords set by the tests, failing like Spring Security's
    private final CredentialsVerifier credentials = (email, raw) -> {
        if (!raw.equals(passwords.get(email))) {
            throw new InvalidCredentialsException("Bad credentials", null);
        }
    };

    private final OtpService otp = new OtpService(new FixedOtpGenerator("123456"), otpCodes,
            new RecordingOtpEventPublisher(), otpAttempts);
    private final RefreshTokenService refreshTokens = new RefreshTokenService(tokens, users, audit, Duration.ofDays(7));
    private final AuthenticationService service = new AuthenticationService(users, raw -> "hashed:" + raw, credentials,
            (email, role) -> "jwt:" + email + ":" + role, refreshTokens, otp, audit);

    private User givenUser(String email, Role role) {
        passwords.put(email, "password123");
        return users.given(new User(null, email, "hashed:password123", null, null, role, LocalDateTime.now()));
    }

    @Test
    void register_storesAHashedUser_andAnswersItsTokens() {
        AuthTokens tokensOut = service.register(EMAIL, "password123");

        assertThat(users.findByEmail(EMAIL)).hasValueSatisfying(user -> {
            assertThat(user.passwordHash()).isEqualTo("hashed:password123");
            assertThat(user.role()).isEqualTo(Role.USER);
            assertThat(user.createdAt()).isCloseTo(LocalDateTime.now(), within(5, ChronoUnit.SECONDS));
        });
        assertThat(tokensOut.accessToken()).isEqualTo("jwt:" + EMAIL + ":USER");
        assertThat(tokens.findByToken(tokensOut.refreshToken())).isPresent();
        assertThat(audit.events()).containsExactly("TOKEN_REFRESHED " + EMAIL, "USER_REGISTER " + EMAIL);
    }

    @Test
    void login_checksThePassword_andSendsACode() {
        givenUser(EMAIL, Role.USER);

        assertThat(service.login(EMAIL, "password123")).isEqualTo(EMAIL);

        assertThat(otpCodes.codes).containsEntry(EMAIL, "123456");
        assertThat(audit.events()).containsExactly("USER_LOGIN_INITIATED " + EMAIL);
    }

    @Test
    void login_withAWrongPassword_sendsNothing_andAuditsNothing() {
        givenUser(EMAIL, Role.USER);

        assertThatThrownBy(() -> service.login(EMAIL, "wrong"))
                .isInstanceOf(InvalidCredentialsException.class).hasMessage("Bad credentials");
        assertThat(otpCodes.codes).isEmpty();
        assertThat(otpAttempts.issued).isEmpty();
        assertThat(audit.events()).isEmpty();
    }

    @Test
    void login_forAUserGoneAfterThePasswordCheck_fails() {
        passwords.put(EMAIL, "password123");

        assertThatThrownBy(() -> service.login(EMAIL, "password123"))
                .isInstanceOf(UserNotFoundException.class).hasMessage("User not found");
    }

    @Test
    void login_whenTooManyCodesWereIssued_auditsTheFailure_andRethrows() {
        givenUser(EMAIL, Role.USER);
        otpAttempts.issued.put(EMAIL, 5L);

        assertThatThrownBy(() -> service.login(EMAIL, "password123")).isInstanceOf(OtpLimitExceededException.class);
        assertThat(audit.events()).containsExactly("USER_LOGIN_FAILED " + EMAIL + " OTP_TOO_MANY_CODES_ISSUED");
    }

    @Test
    void verifyOtp_theRightCode_answersTheTokens_withTheUsersRole() {
        givenUser(EMAIL, Role.ADMIN);
        service.login(EMAIL, "password123");

        AuthTokens tokensOut = service.verifyOtp(EMAIL, "123456");

        assertThat(tokensOut.accessToken()).isEqualTo("jwt:" + EMAIL + ":ADMIN");
        assertThat(tokens.findByToken(tokensOut.refreshToken())).isPresent();
        assertThat(audit.events()).containsExactly("USER_LOGIN_INITIATED " + EMAIL, "TOKEN_REFRESHED " + EMAIL,
                "USER_LOGIN " + EMAIL);
    }

    @Test
    void verifyOtp_aWrongCode_isRefused_andNotAudited() {
        givenUser(EMAIL, Role.USER);
        service.login(EMAIL, "password123");

        assertThatThrownBy(() -> service.verifyOtp(EMAIL, "000000"))
                .isInstanceOf(AuthenticationFailedException.class).hasMessage("Invalid OTP code");
        assertThat(audit.events()).containsExactly("USER_LOGIN_INITIATED " + EMAIL);
    }

    @Test
    void verifyOtp_afterTooManyWrongCodes_auditsTheFailure_andRethrows() {
        givenUser(EMAIL, Role.USER);
        service.login(EMAIL, "password123");
        otpAttempts.failures.put(EMAIL, 4L);

        assertThatThrownBy(() -> service.verifyOtp(EMAIL, "000000")).isInstanceOf(OtpLimitExceededException.class);
        assertThat(audit.events()).last().isEqualTo("USER_LOGIN_FAILED " + EMAIL + " OTP_TOO_MANY_WRONG_CODES");
    }

    @Test
    void verifyOtp_forAUserGoneSinceTheLogin_isUnauthenticated() {
        givenUser(EMAIL, Role.USER);
        service.login(EMAIL, "password123");
        users.remove(EMAIL);

        assertThatThrownBy(() -> service.verifyOtp(EMAIL, "123456"))
                .isInstanceOf(AuthenticationFailedException.class).hasMessage("User not found");
    }

    @Test
    void refresh_rotatesTheToken_andAnswersANewAccessToken() {
        givenUser(EMAIL, Role.ADMIN);
        String first = service.register("other@test.com", "x").refreshToken();
        String current = refreshTokens.createRefreshToken(EMAIL).token();

        AuthTokens next = service.refresh(current);

        assertThat(next.accessToken()).isEqualTo("jwt:" + EMAIL + ":ADMIN");
        assertThat(next.refreshToken()).isNotEqualTo(current);
        assertThat(tokens.findByToken(current)).hasValueSatisfying(t -> assertThat(t.revoked()).isTrue());
        assertThat(tokens.findByToken(next.refreshToken())).hasValueSatisfying(t -> assertThat(t.revoked()).isFalse());
        assertThat(tokens.findByToken(first)).hasValueSatisfying(t -> assertThat(t.revoked()).isFalse());
        assertThatThrownBy(() -> service.refresh(current))
                .isInstanceOf(InvalidRefreshTokenException.class).hasMessage("Refresh token revoked");
    }

    @Test
    void logout_revokesTheToken_andAuditsIt_evenAnUnknownOne() {
        givenUser(EMAIL, Role.USER);
        String token = refreshTokens.createRefreshToken(EMAIL).token();

        service.logout(token);
        service.logout("unknown");

        assertThat(tokens.findByToken(token)).hasValueSatisfying(t -> assertThat(t.revoked()).isTrue());
        assertThat(audit.events()).endsWith("USER_LOGOUT " + token, "USER_LOGOUT unknown");
    }

    @Test
    void anEmailIsAvailable_untilSomeoneRegistersIt() {
        assertThat(service.isEmailAvailable(EMAIL)).isTrue();

        givenUser(EMAIL, Role.USER);

        assertThat(service.isEmailAvailable(EMAIL)).isFalse();
    }

    @Test
    void getProfile_isTheUser_orFails_forAnUnknownOne() {
        User user = givenUser(EMAIL, Role.USER);

        assertThat(service.getProfile(EMAIL)).isEqualTo(user);
        assertThatThrownBy(() -> service.getProfile("nobody@test.com"))
                .isInstanceOf(UserNotFoundException.class).hasMessage("User not found: nobody@test.com");
    }

    @Test
    void updateProfile_setsTheNamesGiven_keepsTheOthers_andAuditsWhatWasSent() {
        givenUser(EMAIL, Role.USER);

        service.updateProfile(EMAIL, "John", "Doe");
        User updated = service.updateProfile(EMAIL, null, "Smith");

        assertThat(updated.firstName()).isEqualTo("John");
        assertThat(updated.lastName()).isEqualTo("Smith");
        assertThat(users.findByEmail(EMAIL)).contains(updated);
        assertThat(audit.events()).containsExactly("USER_PROFILE_UPDATED " + EMAIL + " John Doe",
                "USER_PROFILE_UPDATED " + EMAIL + " null Smith");
    }

    @Test
    void updateProfile_ofAnUnknownUser_fails() {
        assertThatThrownBy(() -> service.updateProfile("nobody@test.com", "John", null))
                .isInstanceOf(UserNotFoundException.class).hasMessage("User not found: nobody@test.com");
        assertThat(audit.events()).isEmpty();
    }
}
