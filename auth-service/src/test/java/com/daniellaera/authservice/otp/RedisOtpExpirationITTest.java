package com.daniellaera.authservice.otp;

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
