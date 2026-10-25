package com.daniellaera.authservice.otp;

import com.daniellaera.authservice.domain.port.out.OtpRepository;
import com.daniellaera.authservice.domain.port.out.OtpAttempts;
import com.daniellaera.authservice.domain.model.OtpCode;
import com.daniellaera.authservice.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RedisOtpExpirationITTest {

    @Autowired
    private OtpRepository otpRepository;

    @Autowired
    private OtpValidator otpValidator;

    @Test
    void otpCode_shouldExpireFromRedis_afterTtlElapses() throws InterruptedException {
        String email = "expiring-otp@test.com";
        otpRepository.save(email, new OtpCode("123456"), Duration.ofSeconds(1));

        assertThat(otpRepository.find(email)).contains("123456");

        Thread.sleep(1500);

        assertThat(otpRepository.find(email)).isEmpty();
    }

    @Autowired
    private OtpAttempts otpAttempts;

    @Test
    void attemptCounters_countWithinTheirWindow_andExpireWithIt() throws InterruptedException {
        String email = "counting@test.com";

        assertThat(otpAttempts.recordFailure(email, Duration.ofSeconds(1))).isEqualTo(1);
        assertThat(otpAttempts.recordFailure(email, Duration.ofSeconds(1))).isEqualTo(2);
        assertThat(otpAttempts.recordIssued(email, Duration.ofSeconds(1))).isEqualTo(1);
        assertThat(otpAttempts.recordIssued(email, Duration.ofSeconds(1))).isEqualTo(2);

        Thread.sleep(1500);

        assertThat(otpAttempts.recordFailure(email, Duration.ofSeconds(1))).isEqualTo(1);
        assertThat(otpAttempts.recordIssued(email, Duration.ofSeconds(1))).isEqualTo(1);
        otpAttempts.resetFailures(email);
        assertThat(otpAttempts.recordFailure(email, Duration.ofSeconds(1))).isEqualTo(1);
    }

    @Test
    void validate_shouldThrow401_afterOtpExpiresInRedis() throws InterruptedException {
        String email = "expiring-otp-validate@test.com";
        otpRepository.save(email, new OtpCode("123456"), Duration.ofSeconds(1));

        Thread.sleep(1500);

        assertThatThrownBy(() -> otpValidator.validate(email, "123456"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("expired or not found");
    }
}
