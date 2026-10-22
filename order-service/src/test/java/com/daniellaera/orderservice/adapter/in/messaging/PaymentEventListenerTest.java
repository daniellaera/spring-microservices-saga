package com.daniellaera.orderservice.adapter.in.messaging;

import com.daniellaera.orderservice.domain.port.in.ApplyPaymentResultUseCase;
import com.daniellaera.orderservice.domain.port.in.PaymentResult;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentEventListenerTest {

    private final ApplyPaymentResultUseCase useCase = mock(ApplyPaymentResultUseCase.class);
    private final PaymentEventListener listener = new PaymentEventListener(useCase, JsonMapper.builder().build());

    @Test
    void mapsEveryFieldToThePaymentResult() {
        listener.handlePaymentEvent("""
                {"orderId":42,"productName":"MacBook Pro","quantity":1,"status":"SUCCESS","price":1299.99,
                 "totalAmount":1399.79,"userEmail":"user@test.com",
                 "items":[{"productName":"MacBook Pro","quantity":1,"price":1299.99,"totalAmount":1299.99}]}""");

        verify(useCase).applyPaymentResult(new PaymentResult(42L, "MacBook Pro", 1, "SUCCESS",
                new BigDecimal("1299.99"), new BigDecimal("1399.79"), "user@test.com"));
    }

    @Test
    void malformedJson_isSwallowed() {
        assertThatCode(() -> listener.handlePaymentEvent("{not json")).doesNotThrowAnyException();

        verifyNoInteractions(useCase);
    }

    @Test
    void useCaseFailure_isSwallowed() {
        doThrow(new IllegalStateException("db down")).when(useCase).applyPaymentResult(any());

        assertThatCode(() -> listener.handlePaymentEvent("{\"orderId\":1,\"status\":\"FAILED\"}"))
                .doesNotThrowAnyException();
    }

    @Test
    void dltHandler_onlyLogs() {
        assertThatCode(() -> listener.handleDlt("{}", new IllegalStateException("poison")))
                .doesNotThrowAnyException();

        verifyNoInteractions(useCase);
    }
}
