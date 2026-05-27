package com.daniellaera.gatewayservice.utils;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

public class JwtUtilTest {

    private JwtUtil jwtUtil;
    private String secret = "mySecretKey1234567890123456789012345678901234";

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", secret);
    }

    @Test
    void isTokenValid_shouldReturnTrue_forValidToken() {
        String token = createToken("test@example.com", "ROLE_USER", 1000 * 60 * 60);
        assertTrue(jwtUtil.isTokenValid(token));
    }

    @Test
    void isTokenValid_shouldReturnFalse_forExpiredToken() {
        String token = createToken("test@example.com", "ROLE_USER", -1000);
        assertFalse(jwtUtil.isTokenValid(token));
    }

    @Test
    void extractEmail_shouldReturnCorrectEmail() {
        String email = "test@example.com";
        String token = createToken(email, "ROLE_USER", 1000 * 60 * 60);
        assertEquals(email, jwtUtil.extractEmail(token));
    }

    @Test
    void extractRole_shouldReturnCorrectRole() {
        String role = "ROLE_ADMIN";
        String token = createToken("test@example.com", role, 1000 * 60 * 60);
        assertEquals(role, jwtUtil.extractRole(token));
    }

    private String createToken(String subject, String role, long expirationMillis) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes());
        return Jwts.builder()
                .subject(subject)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMillis))
                .signWith(key)
                .compact();
    }
}
