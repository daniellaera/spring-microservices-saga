package com.daniellaera.notificationservice.config;

import com.daniellaera.notificationservice.application.NotifyPaymentOutcomeService;
import com.daniellaera.notificationservice.application.SendOtpCodeService;
import com.daniellaera.notificationservice.domain.port.out.EmailSender;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The use cases are plain classes: one bean each. */
@Configuration
public class NotificationUseCaseConfig {

    @Bean
    NotifyPaymentOutcomeService notifyPaymentOutcomeService(EmailSender emailSender) {
        return new NotifyPaymentOutcomeService(emailSender);
    }

    @Bean
    SendOtpCodeService sendOtpCodeService(EmailSender emailSender) {
        return new SendOtpCodeService(emailSender);
    }
}
