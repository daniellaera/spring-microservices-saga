package com.daniellaera.notificationservice.application;

import com.daniellaera.notificationservice.application.fakes.RecordingEmailSender;
import com.daniellaera.notificationservice.domain.model.OtpNotification;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SendOtpCodeServiceTest {

    private final RecordingEmailSender sender = new RecordingEmailSender();
    private final SendOtpCodeService service = new SendOtpCodeService(sender);

    @Test
    void theCode_isSentToTheAddress() {
        service.sendOtpCode(new OtpNotification("buyer@test.com", "123456"));

        assertThat(sender.sent()).singleElement().satisfies(sent -> {
            assertThat(sent.to()).isEqualTo("buyer@test.com");
            assertThat(sent.subject()).contains("verification code");
            assertThat(sent.body()).contains("123456");
        });
    }

    @Test
    void withoutAnAddress_orABlankOne_nothingIsSent() {
        service.sendOtpCode(new OtpNotification(null, "123456"));
        service.sendOtpCode(new OtpNotification("  ", "123456"));

        assertThat(sender.sent()).isEmpty();
    }
}
