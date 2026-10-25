package com.daniellaera.authservice.adapter.out.messaging;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class KafkaAuditTrailTest {

    private final AuditPublisher publisher = mock(AuditPublisher.class);
    private final KafkaAuditTrail auditTrail = new KafkaAuditTrail(publisher);

    @Test
    void eachEvent_hasItsTypeEntityAndPayload() {
        auditTrail.userRegistered("a@b.c");
        auditTrail.loginInitiated("a@b.c");
        auditTrail.loggedIn("a@b.c");
        auditTrail.loginFailed("a@b.c", "OTP_TOO_MANY_WRONG_CODES");
        auditTrail.refreshTokenIssued("a@b.c");
        auditTrail.loggedOut("token");
        auditTrail.profileUpdated("a@b.c", "Ada", null);

        verify(publisher).publish("USER_REGISTER", "a@b.c", "USER", "a@b.c", "a@b.c");
        verify(publisher).publish("USER_LOGIN_INITIATED", "a@b.c", "USER", "a@b.c", "a@b.c");
        verify(publisher).publish("USER_LOGIN", "a@b.c", "USER", "a@b.c", "a@b.c");
        verify(publisher).publish("USER_LOGIN_FAILED", "a@b.c", "USER", "a@b.c",
                Map.of("reason", "OTP_TOO_MANY_WRONG_CODES"));
        verify(publisher).publish("TOKEN_REFRESHED", "a@b.c", "USER", "a@b.c", "a@b.c");
        verify(publisher).publish("USER_LOGOUT", null, "USER", null, "token");
        verify(publisher).publish("USER_PROFILE_UPDATED", "a@b.c", "USER", "a@b.c",
                new KafkaAuditTrail.ProfileUpdatePayload("Ada", null));
    }
}
