package com.daniellaera.authservice.otp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpServiceImplTest {

    @Mock private OtpGenerator otpGenerator;
    @Mock private OtpRepository otpRepository;
    @Mock private OtpValidator otpValidator;
    @Mock private OtpEventPublisher otpEventPublisher;
    @Mock private OtpAttempts otpAttempts;

    @InjectMocks
    private OtpServiceImpl otpService;

    @Test
    void initiateOtp_shouldGenerateStoreAndPublish() {
        OtpCode otp = new OtpCode("123456");
        when(otpAttempts.recordIssued("john@test.com", Duration.ofMinutes(15))).thenReturn(1L);
        when(otpGenerator.generate()).thenReturn(otp);

        otpService.initiateOtp("john@test.com");

        verify(otpRepository).save(eq("john@test.com"), eq(otp), eq(Duration.ofMinutes(5)));
        verify(otpEventPublisher).publishOtpGenerated("john@test.com", otp);
        // a new code starts with a clean count of wrong codes
        verify(otpAttempts).resetFailures("john@test.com");
    }

    @Test
    void initiateOtp_shouldStillIssue_theFifthCodeOfTheWindow() {
        when(otpAttempts.recordIssued("john@test.com", Duration.ofMinutes(15))).thenReturn(5L);
        when(otpGenerator.generate()).thenReturn(new OtpCode("123456"));

        otpService.initiateOtp("john@test.com");

        verify(otpEventPublisher).publishOtpGenerated(eq("john@test.com"), any());
    }

    @Test
    void initiateOtp_shouldRefuseWith429_aSixthCodeInTheWindow() {
        when(otpAttempts.recordIssued("john@test.com", Duration.ofMinutes(15))).thenReturn(6L);

        assertThatThrownBy(() -> otpService.initiateOtp("john@test.com"))
                .isInstanceOfSatisfying(OtpLimitExceededException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(e.auditReason()).isEqualTo("OTP_TOO_MANY_CODES_ISSUED");
                    assertThat(e.getReason()).isEqualTo("Too many login attempts — please try again in 15 minutes");
                });
        verifyNoInteractions(otpGenerator, otpEventPublisher);
        verify(otpRepository, never()).save(anyString(), any(), any());
    }

    @Test
    void verifyOtp_shouldDelegateToValidator() {
        otpService.verifyOtp("john@test.com", "123456");

        verify(otpValidator).validate("john@test.com", "123456");
    }
}
