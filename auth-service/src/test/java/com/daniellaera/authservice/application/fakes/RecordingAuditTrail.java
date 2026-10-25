package com.daniellaera.authservice.application.fakes;

import com.daniellaera.authservice.domain.port.out.AuditTrail;

import java.util.ArrayList;
import java.util.List;

public class RecordingAuditTrail implements AuditTrail {

    private final List<String> events = new ArrayList<>();

    @Override public void userRegistered(String email) { events.add("USER_REGISTER " + email); }
    @Override public void loginInitiated(String email) { events.add("USER_LOGIN_INITIATED " + email); }
    @Override public void loggedIn(String email) { events.add("USER_LOGIN " + email); }
    @Override public void loginFailed(String email, String reason) { events.add("USER_LOGIN_FAILED " + email + " " + reason); }
    @Override public void refreshTokenIssued(String email) { events.add("TOKEN_REFRESHED " + email); }
    @Override public void loggedOut(String refreshToken) { events.add("USER_LOGOUT " + refreshToken); }
    @Override public void profileUpdated(String email, String firstName, String lastName) {
        events.add("USER_PROFILE_UPDATED " + email + " " + firstName + " " + lastName);
    }

    public List<String> events() {
        return List.copyOf(events);
    }
}
