package com.daniellaera.notificationservice.consumer;

import com.daniellaera.notificationservice.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class NotificationConsumerITTest {

    private static final String PAYMENT_TOPIC = "payment-topic";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @MockitoBean
    private JavaMailSender mailSender;

    @BeforeEach
    void resetMocks() {
        Mockito.reset(mailSender);
    }

    @Test
    void consumeSuccessEvent_sendsConfirmationEmail() {
        String json = """
                {
                  "orderId": 1,
                  "productName": "MacBook Pro",
                  "quantity": 2,
                  "price": 1299.99,
                  "totalAmount": 2599.98,
                  "status": "SUCCESS",
                  "userEmail": "buyer@test.com"
                }
                """;

        kafkaTemplate.send(PAYMENT_TOPIC, json);

        await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
                    verify(mailSender).send(captor.capture());
                    SimpleMailMessage sent = captor.getValue();
                    assertThat(sent.getTo()).containsExactly("buyer@test.com");
                    assertThat(sent.getSubject()).contains("confirmed").contains("MacBook Pro");
                    assertThat(sent.getText()).contains("MacBook Pro").contains("1299.99").contains("2599.98");
                });
    }

    @Test
    void consumeFailedEvent_sendsCancellationEmail() {
        String json = """
                {
                  "orderId": 2,
                  "productName": "iPhone 16",
                  "quantity": 1,
                  "price": 999.99,
                  "totalAmount": 999.99,
                  "status": "FAILED",
                  "userEmail": "buyer@test.com"
                }
                """;

        kafkaTemplate.send(PAYMENT_TOPIC, json);

        await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
                    verify(mailSender).send(captor.capture());
                    SimpleMailMessage sent = captor.getValue();
                    assertThat(sent.getTo()).containsExactly("buyer@test.com");
                    assertThat(sent.getSubject()).contains("cancelled").contains("iPhone 16");
                    assertThat(sent.getText()).contains("iPhone 16").contains("999.99");
                });
    }

    @Test
    void consumeEventWithNoEmail_doesNotSendAnyEmail() {
        String json = """
                {
                  "orderId": 3,
                  "productName": "AirPods",
                  "quantity": 1,
                  "price": 249.99,
                  "totalAmount": 249.99,
                  "status": "SUCCESS",
                  "userEmail": null
                }
                """;

        kafkaTemplate.send(PAYMENT_TOPIC, json);

        await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .during(3, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        Mockito.verify(mailSender, Mockito.never()).send(any(SimpleMailMessage.class))
                );
    }
}
