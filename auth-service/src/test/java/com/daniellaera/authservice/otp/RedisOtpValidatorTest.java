package com.daniellaera.authservice.otp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisOtpValidatorTest {

    @Mock private OtpRepository otpRepository;

    @InjectMocks
    private RedisOtpValidator validator;

    @Test
    void validate_shouldSucceedAndDeleteOtp_whenCodeMatches() {
        when(otpRepository.find("john@test.com")).thenReturn(Optional.of("123456"));

        validator.validate("john@test.com", "123456");

        verify(otpRepository).delete("john@test.com");
    }

    @Test
    void validate_shouldThrow401_whenCodeDoesNotMatch() {
        when(otpRepository.find("john@test.com")).thenReturn(Optional.of("123456"));

        assertThatThrownBy(() -> validator.validate("john@test.com", "000000"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid OTP code");
    }

    @Test
    void validate_shouldThrow401_whenOtpNotFound() {
        when(otpRepository.find("john@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.validate("john@test.com", "123456"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("expired or not found");
    }
}
