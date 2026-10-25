package com.daniellaera.notificationservice.application;

import com.daniellaera.notificationservice.domain.model.EmailMessage;
import com.daniellaera.notificationservice.domain.model.OtpNotification;
import com.daniellaera.notificationservice.domain.port.in.SendOtpCodeUseCase;
import com.daniellaera.notificationservice.domain.port.out.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendOtpCodeService implements SendOtpCodeUseCase {

    private static final Logger log = LoggerFactory.getLogger(SendOtpCodeService.class);

    private final EmailSender emailSender;

    public SendOtpCodeService(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    @Override
    public void sendOtpCode(OtpNotification notification) {
        if (!notification.hasRecipient()) {
            log.warn("=== OTP: no email address — skipping");
            return;
        }

        String subject = "Your verification code — Online Shop";
        String body = """
                Hi,

                Your verification code is:

                    %s

                This code expires in 5 minutes.
                If you didn't request this, ignore this email.

                — Online Shop Security
                """.formatted(notification.otp());

        emailSender.send(new EmailMessage(notification.email(), subject, body));
    }
}
