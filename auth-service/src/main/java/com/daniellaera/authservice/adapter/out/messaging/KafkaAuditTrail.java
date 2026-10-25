package com.daniellaera.authservice.adapter.out.messaging;

import com.daniellaera.authservice.domain.port.out.AuditTrail;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

/** The audit events of auth-service, sent by AuditPublisher (asynchronously, never throwing). */
@Component
@RequiredArgsConstructor
public class KafkaAuditTrail implements AuditTrail {

    private static final String USER = "USER";

    private final AuditPublisher auditPublisher;

    // USER_PROFILE_UPDATED's payload: the names as they were sent
    record ProfileUpdatePayload(String firstName, String lastName) {}

    @Override
    public void userRegistered(String email) {
        auditPublisher.publish("USER_REGISTER", email, USER, email, email);
    }

    @Override
    public void loginInitiated(String email) {
        auditPublisher.publish("USER_LOGIN_INITIATED", email, USER, email, email);
    }

    @Override
    public void loggedIn(String email) {
        auditPublisher.publish("USER_LOGIN", email, USER, email, email);
    }

    @Override
    public void loginFailed(String email, String reason) {
        auditPublisher.publish("USER_LOGIN_FAILED", email, USER, email, Map.of("reason", reason));
    }

    @Override
    public void refreshTokenIssued(String email) {
        auditPublisher.publish("TOKEN_REFRESHED", email, USER, email, email);
    }

    // never the token itself: a short prefix of its SHA-256, enough to match a token one holds, useless as a credential
    @Override
    public void loggedOut(String refreshToken) {
        auditPublisher.publish("USER_LOGOUT", null, USER, null, "sha256:" + sha256Prefix(refreshToken));
    }

    @Override
    public void profileUpdated(String email, String firstName, String lastName) {
        auditPublisher.publish("USER_PROFILE_UPDATED", email, USER, email, new ProfileUpdatePayload(firstName, lastName));
    }

    static String sha256Prefix(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 6);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
