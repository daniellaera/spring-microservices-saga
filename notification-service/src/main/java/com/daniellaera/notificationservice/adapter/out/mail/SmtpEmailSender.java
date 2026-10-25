package com.daniellaera.notificationservice.adapter.out.mail;

import com.daniellaera.notificationservice.domain.model.EmailMessage;
import com.daniellaera.notificationservice.domain.port.out.EmailSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Override
    public void send(EmailMessage email) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(email.to());
            message.setSubject(email.subject());
            message.setText(email.body());
            mailSender.send(message);
            log.info("=== Email sent to {} subject: {}", email.to(), email.subject());
        } catch (Exception e) {
            log.error("=== Failed to send email to {}: {}", email.to(), e.getMessage());
            // never throw — email failure must NOT break the saga
        }
    }
}
