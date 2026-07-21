package com.daniellaera.notificationservice.consumer;

import com.daniellaera.notificationservice.dto.OtpMessage;
import com.daniellaera.notificationservice.service.EmailNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpNotificationConsumer {

    private final ObjectMapper objectMapper;
    private final EmailNotificationService emailNotificationService;

    @KafkaListener(topics = "otp-topic", groupId = "otp-notification-group")
    public void handleOtpEvent(String message) {
        try {
            OtpMessage otpMessage = objectMapper.readValue(message, OtpMessage.class);
            log.info("=== OTP notification: sending to {}", otpMessage.email());
            emailNotificationService.sendOtpCode(otpMessage);
        } catch (Exception e) {
            log.error("=== OTP: failed to process message: {}", e.getMessage());
        }
    }
}
