package com.daniellaera.authservice.otp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaOtpEventPublisher implements OtpEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Override
    @Async
    public void publishOtpGenerated(String email, OtpCode otp) {
        try {
            Map<String, String> event = Map.of(
                    "email", email,
                    "otp", otp.value(),
                    "timestamp", LocalDateTime.now().toString()
            );
            kafkaTemplate.send("otp-topic", objectMapper.writeValueAsString(event));
            log.info("=== OTP event published for {}", email);
        } catch (Exception e) {
            log.error("=== OTP: failed to publish event: {}", e.getMessage());
        }
    }
}
