package com.daniellaera.notificationservice.contract;

import com.daniellaera.notificationservice.adapter.in.messaging.NotificationConsumer;
import com.daniellaera.notificationservice.adapter.in.messaging.OtpNotificationConsumer;
import com.daniellaera.notificationservice.adapter.out.mail.SmtpEmailSender;
import com.daniellaera.notificationservice.application.NotifyPaymentOutcomeService;
import com.daniellaera.notificationservice.application.SendOtpCodeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The wire contracts of notification-service, recorded from the code before the hexagonal refactor and kept
 * byte-identical (STRICT): for each consumed message (payment-topic, otp-topic) in src/test/resources/contracts/,
 * the emails it sends (from, to, subject, text), none when there is nobody to send to. The amounts are formatted
 * with the JVM's default locale, as in production; Locale.ROOT here, like the containers. -Dcontracts.record=true
 * rewrites the fixtures; only ever on code whose behaviour is the reference.
 */
class EmailContractTest {

    private static final Path CONTRACTS = Path.of("src/test/resources/contracts");
    private static final boolean RECORD = Boolean.getBoolean("contracts.record");

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private Locale previousLocale;

    @BeforeEach
    void rootLocale() {
        previousLocale = Locale.getDefault();
        Locale.setDefault(Locale.ROOT);
    }

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(previousLocale);
    }

    @Test
    void confirmedMultiItemOrder() throws Exception {
        assertEmails("payment-success-multi-item.json", "emails-payment-success-multi-item.json");
    }

    @Test
    void confirmedLegacySingleItemOrder() throws Exception {
        assertEmails("payment-success-legacy-single-item.json", "emails-payment-success-legacy-single-item.json");
    }

    @Test
    void cancelledOrder() throws Exception {
        assertEmails("payment-failed.json", "emails-payment-failed.json");
    }

    @Test
    void orderWithoutEmail_andUnknownStatus_sendNothing() throws Exception {
        assertEmails("payment-success-without-email.json", "emails-none.json");
        assertEmails("payment-unknown-status.json", "emails-none.json");
    }

    @Test
    void otpCode() throws Exception {
        assertEmails("otp.json", "emails-otp.json");
    }

    // ─── the classes under contract ──────────────────────────────────────────

    private void deliver(String message) {
        SmtpEmailSender sender = new SmtpEmailSender(mailSender);
        ReflectionTestUtils.setField(sender, "fromEmail", "shop@daniellaera.com");
        if (message.contains("\"otp\"")) {
            new OtpNotificationConsumer(JsonMapper.builder().build(), new SendOtpCodeService(sender))
                    .handleOtpEvent(message);
        } else {
            new NotificationConsumer(JsonMapper.builder().build(), new NotifyPaymentOutcomeService(sender))
                    .handlePaymentEvent(message);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void assertEmails(String messageFixture, String emailsFixture) throws Exception {
        org.mockito.Mockito.clearInvocations(mailSender);
        deliver(read(messageFixture));

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeast(0)).send(sent.capture());
        List<Map<String, Object>> emails = new ArrayList<>();
        for (SimpleMailMessage email : sent.getAllValues()) {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("from", email.getFrom());
            json.put("to", email.getTo());
            json.put("subject", email.getSubject());
            json.put("text", email.getText());
            emails.add(json);
        }
        String actual = JsonMapper.builder().build().writeValueAsString(emails);
        if (RECORD) {
            Files.writeString(CONTRACTS.resolve(emailsFixture), JsonMapper.builder().build()
                    .writerWithDefaultPrettyPrinter().writeValueAsString(emails) + "\n");
            return;
        }
        JSONAssert.assertEquals(read(emailsFixture), actual, JSONCompareMode.STRICT);
    }

    private static String read(String fixture) {
        try {
            return Files.readString(CONTRACTS.resolve(fixture));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
