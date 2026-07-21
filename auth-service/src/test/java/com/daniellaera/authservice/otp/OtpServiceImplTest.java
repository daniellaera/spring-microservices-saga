package com.daniellaera.authservice.otp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpServiceImplTest {

    @Mock private OtpGenerator otpGenerator;
    @Mock private OtpRepository otpRepository;
    @Mock private OtpValidator otpValidator;
    @Mock private OtpEventPublisher otpEventPublisher;

    @InjectMocks
    private OtpServiceImpl otpService;

    @Test
    void initiateOtp_shouldGenerateStoreAndPublish() {
        OtpCode otp = new OtpCode("123456");
        when(otpGenerator.generate()).thenReturn(otp);

        otpService.initiateOtp("john@test.com");

        verify(otpRepository).save(eq("john@test.com"), eq(otp), eq(Duration.ofMinutes(5)));
        verify(otpEventPublisher).publishOtpGenerated("john@test.com", otp);
    }

    @Test
    void verifyOtp_shouldDelegateToValidator() {
        otpService.verifyOtp("john@test.com", "123456");

        verify(otpValidator).validate("john@test.com", "123456");
    }
}
