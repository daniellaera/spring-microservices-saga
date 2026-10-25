package com.daniellaera.notificationservice.adapter.in.messaging;

import com.daniellaera.notificationservice.domain.model.OtpNotification;
import com.daniellaera.notificationservice.domain.port.in.SendOtpCodeUseCase;
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
    private final SendOtpCodeUseCase sendOtpCodeUseCase;

    @KafkaListener(topics = "otp-topic", groupId = "otp-notification-group")
    public void handleOtpEvent(String message) {
        try {
            OtpMessage otpMessage = objectMapper.readValue(message, OtpMessage.class);
            log.info("=== OTP notification: sending to {}", otpMessage.email());
            sendOtpCodeUseCase.sendOtpCode(new OtpNotification(otpMessage.email(), otpMessage.otp()));
        } catch (Exception e) {
            log.error("=== OTP: failed to process message: {}", e.getMessage());
        }
    }
}
