package com.daniellaera.notificationservice.application.fakes;

import com.daniellaera.notificationservice.domain.model.EmailMessage;
import com.daniellaera.notificationservice.domain.port.out.EmailSender;

import java.util.ArrayList;
import java.util.List;

public class RecordingEmailSender implements EmailSender {

    private final List<EmailMessage> sent = new ArrayList<>();

    @Override
    public void send(EmailMessage message) {
        sent.add(message);
    }

    public List<EmailMessage> sent() {
        return List.copyOf(sent);
    }
}
