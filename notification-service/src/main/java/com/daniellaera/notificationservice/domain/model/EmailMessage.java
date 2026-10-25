package com.daniellaera.notificationservice.domain.model;

/** A plain-text email to one recipient; the sender address is the mail adapter's. */
public record EmailMessage(String to, String subject, String body) {
}
