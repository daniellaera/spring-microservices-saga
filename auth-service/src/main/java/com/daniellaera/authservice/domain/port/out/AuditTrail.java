package com.daniellaera.authservice.domain.port.out;

/** What auth-service records on audit-topic. Fire-and-forget: never throws, never waits. */
public interface AuditTrail {

    void userRegistered(String email);

    void loginInitiated(String email);

    void loggedIn(String email);

    void loginFailed(String email, String reason);

    // a refresh token was issued (TOKEN_REFRESHED)
    void refreshTokenIssued(String email);

    // the user is not known here: the payload is the refresh token that was revoked
    void loggedOut(String refreshToken);

    // the names as they were sent, null for one left unchanged
    void profileUpdated(String email, String firstName, String lastName);
}
