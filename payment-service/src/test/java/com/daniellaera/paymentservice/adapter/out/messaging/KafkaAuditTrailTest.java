package com.daniellaera.paymentservice.adapter.out.messaging;

import com.daniellaera.paymentservice.domain.model.PaymentStatus;
import com.daniellaera.paymentservice.domain.model.PaymentTransaction;
import com.daniellaera.paymentservice.domain.port.in.CreatePaymentIntentCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class KafkaAuditTrailTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private KafkaTemplate<String, String> kafkaTemplate;
    private KafkaAuditTrail auditTrail;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        auditTrail = new KafkaAuditTrail(kafkaTemplate, objectMapper);
    }

    @Test
    void everyAuditMethod_isAsync() {
        assertThat(Arrays.stream(KafkaAuditTrail.class.getDeclaredMethods())
                .filter(m -> m.getName().startsWith("payment")))
                .hasSize(2)
                .allSatisfy(m -> assertThat(m.isAnnotationPresent(Async.class)).isTrue());
    }

    @Test
    void failedTransaction_isAuditedAsPaymentFailedForTheOrder() {
        auditTrail.paymentProcessed(new PaymentTransaction(1L, 9L, BigDecimal.TEN, PaymentStatus.FAILED, null, null),
                "user@test.com");

        Map<?, ?> message = sentMessage();
        assertThat(message.get("eventType")).isEqualTo("PAYMENT_FAILED");
        assertThat(message.get("entityType")).isEqualTo("PAYMENT");
        assertThat(message.get("entityId")).isEqualTo("9");
        assertThat(message.get("serviceName")).isEqualTo("payment-service");
    }

    @Test
    void withoutEmail_isAuditedAsSystem() {
        auditTrail.paymentInitiated("pi_1", new CreatePaymentIntentCommand(100L, "usd", "Case", null));

        assertThat(sentMessage().get("userEmail")).isEqualTo("system");
    }

    @Test
    void kafkaFailure_isSwallowed() {
        when(kafkaTemplate.send(eq("audit-topic"), anyString())).thenThrow(new IllegalStateException("broker down"));

        assertThatCode(() -> auditTrail.paymentInitiated("pi_1",
                new CreatePaymentIntentCommand(100L, "usd", "Case", "user@test.com"))).doesNotThrowAnyException();
    }

    private Map<?, ?> sentMessage() {
        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("audit-topic"), message.capture());
        return objectMapper.readValue(message.getValue(), Map.class);
    }
}
