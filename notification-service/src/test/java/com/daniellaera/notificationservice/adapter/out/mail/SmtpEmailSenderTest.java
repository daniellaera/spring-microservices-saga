package com.daniellaera.notificationservice.adapter.out.mail;

import com.daniellaera.notificationservice.domain.model.EmailMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SmtpEmailSenderTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final SmtpEmailSender sender = new SmtpEmailSender(mailSender);

    {
        ReflectionTestUtils.setField(sender, "fromEmail", "shop@test.com");
    }

    @Test
    void sendsAPlainTextEmail_fromTheShopsAddress() {
        sender.send(new EmailMessage("buyer@test.com", "Subject", "Body"));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getFrom()).isEqualTo("shop@test.com");
        assertThat(sent.getTo()).containsExactly("buyer@test.com");
        assertThat(sent.getSubject()).isEqualTo("Subject");
        assertThat(sent.getText()).isEqualTo("Body");
    }

    @Test
    void anSmtpFailure_isSwallowed() {
        doThrow(new RuntimeException("SMTP down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> sender.send(new EmailMessage("buyer@test.com", "Subject", "Body")))
                .doesNotThrowAnyException();
    }
}
