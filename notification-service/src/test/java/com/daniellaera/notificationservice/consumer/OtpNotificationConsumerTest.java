package com.daniellaera.notificationservice.consumer;

import com.daniellaera.notificationservice.dto.OtpMessage;
import com.daniellaera.notificationservice.service.EmailNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OtpNotificationConsumerTest {

    @Mock
    private EmailNotificationService emailNotificationService;

    private OtpNotificationConsumer consumer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        consumer = new OtpNotificationConsumer(objectMapper, emailNotificationService);
    }

    private String toJson(OtpMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void handleOtpEvent_callsSendOtpCode() {
        OtpMessage message = new OtpMessage("buyer@test.com", "123456", "2026-07-16T10:00:00");

        consumer.handleOtpEvent(toJson(message));

        verify(emailNotificationService).sendOtpCode(message);
    }

    @Test
    void handleOtpEvent_invalidJson_doesNotThrow() {
        assertThatCode(() -> consumer.handleOtpEvent("not-valid-json"))
                .doesNotThrowAnyException();
        verifyNoInteractions(emailNotificationService);
    }

    @Test
    void handleOtpEvent_emptyMessage_doesNotThrow() {
        assertThatCode(() -> consumer.handleOtpEvent(""))
                .doesNotThrowAnyException();
        verifyNoInteractions(emailNotificationService);
    }
}
