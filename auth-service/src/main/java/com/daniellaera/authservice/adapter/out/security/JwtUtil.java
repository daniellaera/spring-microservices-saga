package com.daniellaera.authservice.adapter.out.security;

import com.daniellaera.authservice.domain.model.Role;
import com.daniellaera.authservice.domain.port.out.AccessTokenIssuer;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
@Slf4j
public class JwtUtil implements AccessTokenIssuer {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration}")
    private long expiration;

    @PostConstruct
    public void validateSecret() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                "JWT_SECRET environment variable is not set — application cannot start safely");
        }
        if (secret.length() < 44) {
            throw new IllegalStateException(
                "JWT_SECRET must be at least 44 characters — current length: "
                + secret.length());
        }
        log.info("=== JWT secret validated — length: {} chars", secret.length());
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generateToken(String email, String role) {
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    @Override
    public String issue(String email, Role role) {
        return generateToken(email, role.name());
    }
}
