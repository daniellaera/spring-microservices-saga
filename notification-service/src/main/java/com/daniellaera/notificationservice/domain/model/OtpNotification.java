package com.daniellaera.notificationservice.domain.model;

/** A one-time code to send to an address (may be missing). */
public record OtpNotification(String email, String otp) {

    public boolean hasRecipient() {
        return email != null && !email.isBlank();
    }
}
