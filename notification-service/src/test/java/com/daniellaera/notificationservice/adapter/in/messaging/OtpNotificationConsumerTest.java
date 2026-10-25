package com.daniellaera.notificationservice.adapter.in.messaging;

import com.daniellaera.notificationservice.domain.model.OtpNotification;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class OtpNotificationConsumerTest {

    private final List<OtpNotification> sent = new ArrayList<>();
    private final OtpNotificationConsumer consumer = new OtpNotificationConsumer(JsonMapper.builder().build(), sent::add);

    @Test
    void anOtpMessage_isSentOn() {
        consumer.handleOtpEvent("{\"email\":\"buyer@test.com\",\"otp\":\"123456\",\"timestamp\":\"2026-07-16T10:00:00\"}");

        assertThat(sent).containsExactly(new OtpNotification("buyer@test.com", "123456"));
    }

    @Test
    void anUnreadableOrEmptyMessage_isSwallowed() {
        assertThatCode(() -> consumer.handleOtpEvent("not-json")).doesNotThrowAnyException();
        assertThatCode(() -> consumer.handleOtpEvent("")).doesNotThrowAnyException();

        assertThat(sent).isEmpty();
    }
}
