package com.daniellaera.authservice.otp;

import com.daniellaera.authservice.domain.port.out.OtpRepository;
import com.daniellaera.authservice.domain.port.out.OtpAttempts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisOtpValidatorTest {

    @Mock private OtpRepository otpRepository;
    @Mock private OtpAttempts otpAttempts;

    @InjectMocks
    private RedisOtpValidator validator;

    @Test
    void validate_shouldSucceedAndDeleteOtp_whenCodeMatches() {
        when(otpRepository.find("john@test.com")).thenReturn(Optional.of("123456"));

        validator.validate("john@test.com", "123456");

        verify(otpRepository).delete("john@test.com");
        verify(otpAttempts).resetFailures("john@test.com");
    }

    @Test
    void validate_shouldThrow401_whenCodeDoesNotMatch() {
        when(otpRepository.find("john@test.com")).thenReturn(Optional.of("123456"));
        when(otpAttempts.recordFailure("john@test.com", Duration.ofMinutes(5))).thenReturn(1L);

        assertThatThrownBy(() -> validator.validate("john@test.com", "000000"))
                .isInstanceOf(ResponseStatusException.class)
                .isNotInstanceOf(OtpLimitExceededException.class)
                .hasMessageContaining("Invalid OTP code");
        verify(otpRepository, never()).delete(anyString());
    }

    @Test
    void validate_shouldKeepTheOtp_untilTheFifthWrongCode() {
        when(otpRepository.find("john@test.com")).thenReturn(Optional.of("123456"));
        when(otpAttempts.recordFailure("john@test.com", Duration.ofMinutes(5))).thenReturn(4L);

        assertThatThrownBy(() -> validator.validate("john@test.com", "000000"))
                .isInstanceOf(ResponseStatusException.class)
                .isNotInstanceOf(OtpLimitExceededException.class);
        verify(otpRepository, never()).delete(anyString());
    }

    @Test
    void validate_shouldInvalidateTheOtpAndThrow429_onTheFifthWrongCode() {
        when(otpRepository.find("john@test.com")).thenReturn(Optional.of("123456"));
        when(otpAttempts.recordFailure("john@test.com", Duration.ofMinutes(5))).thenReturn(5L);

        assertThatThrownBy(() -> validator.validate("john@test.com", "000000"))
                .isInstanceOfSatisfying(OtpLimitExceededException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(e.auditReason()).isEqualTo("OTP_TOO_MANY_WRONG_CODES");
                    assertThat(e.getReason()).isEqualTo("Too many invalid codes — please login again");
                });
        verify(otpRepository).delete("john@test.com");
        verify(otpAttempts).resetFailures("john@test.com");
    }

    @Test
    void validate_shouldThrow401_whenOtpNotFound() {
        when(otpRepository.find("john@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.validate("john@test.com", "123456"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("expired or not found");
        // no OTP, nothing to guess: a wrong code for an unknown email is not counted
        verify(otpAttempts, never()).recordFailure(anyString(), any());
    }
}
