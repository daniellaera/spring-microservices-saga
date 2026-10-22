package com.daniellaera.orderservice.config;

import com.daniellaera.orderservice.application.ApplyPaymentResultService;
import com.daniellaera.orderservice.application.GetOrderService;
import com.daniellaera.orderservice.application.ListOrdersService;
import com.daniellaera.orderservice.application.PlaceOrderService;
import com.daniellaera.orderservice.domain.port.out.AuditTrail;
import com.daniellaera.orderservice.domain.port.out.OrderEventPublisher;
import com.daniellaera.orderservice.domain.port.out.OrderRepository;
import com.daniellaera.orderservice.domain.port.out.OrderStatusNotifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OrderUseCaseConfig {

    @Bean
    public PlaceOrderService placeOrderService(OrderRepository orderRepository,
                                               OrderEventPublisher orderEventPublisher,
                                               AuditTrail auditTrail) {
        return new PlaceOrderService(orderRepository, orderEventPublisher, auditTrail);
    }

    @Bean
    public GetOrderService getOrderService(OrderRepository orderRepository) {
        return new GetOrderService(orderRepository);
    }

    @Bean
    public ListOrdersService listOrdersService(OrderRepository orderRepository) {
        return new ListOrdersService(orderRepository);
    }

    @Bean
    public ApplyPaymentResultService applyPaymentResultService(OrderRepository orderRepository,
                                                               AuditTrail auditTrail,
                                                               OrderStatusNotifier orderStatusNotifier) {
        return new ApplyPaymentResultService(orderRepository, auditTrail, orderStatusNotifier);
    }
}
