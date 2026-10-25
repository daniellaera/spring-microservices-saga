package com.daniellaera.authservice.domain.model;

import java.time.Instant;

/** A long-lived token that gets a new access token once, after which it is revoked (rotation). */
public record RefreshToken(String token, String userEmail, Role userRole, Instant expiryDate, boolean revoked) {

    public static RefreshToken issue(String token, String userEmail, Role userRole, Instant expiryDate) {
        return new RefreshToken(token, userEmail, userRole, expiryDate, false);
    }

    public boolean isExpired(Instant now) {
        return expiryDate.isBefore(now);
    }

    public RefreshToken revoke() {
        return new RefreshToken(token, userEmail, userRole, expiryDate, true);
    }
}
