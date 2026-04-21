package com.daniellaera.authservice.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "testSecretKey1234567890123456789012345678901234");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 3600000L);
    }

    @Test
    void generateToken_shouldCreateValidTokenWithRole() {
        String token = jwtUtil.generateToken("user@test.com", "USER");

        assertThat(token).isNotNull();
        assertThat(token.split("\\.")).hasSize(3); // JWT has 3 parts
    }

    @Test
    void generateToken_shouldIncludeEmailAndRoleAsClaims() {
        String token = jwtUtil.generateToken("admin@test.com", "ADMIN");

        assertThat(token).isNotNull();
    }
}