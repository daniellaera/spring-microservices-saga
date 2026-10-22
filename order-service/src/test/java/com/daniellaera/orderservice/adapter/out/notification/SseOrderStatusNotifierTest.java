package com.daniellaera.orderservice.adapter.out.notification;

import com.daniellaera.orderservice.domain.model.Order;
import com.daniellaera.orderservice.domain.model.OrderItem;
import com.daniellaera.orderservice.domain.model.OrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SseOrderStatusNotifierTest {

    private final SseEmitterRegistry registry = mock(SseEmitterRegistry.class);
    private final SseOrderStatusNotifier notifier = new SseOrderStatusNotifier(registry);

    @Test
    void orderWithEmail_isPushedToThatUser() {
        notifier.statusChanged(order("user@test.com"));

        verify(registry).pushToUser("user@test.com", new OrderStatusUpdate(7L, "CONFIRMED", "user@test.com"));
        verify(registry, never()).pushToAll(any());
    }

    @Test
    void orderWithoutEmail_isPushedToAll() {
        notifier.statusChanged(order(null));

        verify(registry).pushToAll(new OrderStatusUpdate(7L, "CONFIRMED", null));
        verify(registry, never()).pushToUser(anyString(), any());
    }

    @Test
    void pushFailure_isSwallowed() {
        doThrow(new IllegalStateException("emitter gone")).when(registry).pushToUser(anyString(), any());

        assertThatCode(() -> notifier.statusChanged(order("user@test.com"))).doesNotThrowAnyException();
    }

    private static Order order(String userEmail) {
        return Order.restore(7L, userEmail, null, OrderStatus.CONFIRMED, BigDecimal.TEN, null,
                "Item", 1, BigDecimal.TEN, List.of(new OrderItem(1L, "Item", 1, BigDecimal.TEN, BigDecimal.TEN)));
    }
}
