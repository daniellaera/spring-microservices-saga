package com.daniellaera.notificationservice.domain.port.out;

import com.daniellaera.notificationservice.domain.model.EmailMessage;

/** Sends an email. Never throws: a failed email must not break the saga. */
public interface EmailSender {

    void send(EmailMessage message);
}
