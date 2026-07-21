package com.daniellaera.authservice.otp;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SecureOtpGeneratorTest {

    private final SecureOtpGenerator generator = new SecureOtpGenerator();

    @Test
    void generate_shouldProduceSixDigitCode() {
        OtpCode otp = generator.generate();

        assertThat(otp.value()).hasSize(6);
        assertThat(otp.value()).matches("\\d{6}");
    }

    @Test
    void generate_shouldProduceCodeInValidRange() {
        for (int i = 0; i < 100; i++) {
            int code = Integer.parseInt(generator.generate().value());
            assertThat(code).isBetween(100000, 999999);
        }
    }

    @Test
    void generate_shouldProduceDifferentCodes() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            codes.add(generator.generate().value());
        }

        assertThat(codes.size()).isGreaterThan(1);
    }
}
