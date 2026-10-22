package com.daniellaera.paymentservice.config;

import com.daniellaera.paymentservice.application.CreatePaymentIntentService;
import com.daniellaera.paymentservice.application.ProcessInventoryResultService;
import com.daniellaera.paymentservice.application.TransactionQueryService;
import com.daniellaera.paymentservice.application.VerifyPaymentService;
import com.daniellaera.paymentservice.domain.port.out.AuditTrail;
import com.daniellaera.paymentservice.domain.port.out.PaymentGateway;
import com.daniellaera.paymentservice.domain.port.out.PaymentResultPublisher;
import com.daniellaera.paymentservice.domain.port.out.TransactionRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PaymentUseCaseConfig {

    @Bean
    public ProcessInventoryResultService processInventoryResultService(PaymentGateway paymentGateway,
                                                                       TransactionRepository transactionRepository,
                                                                       AuditTrail auditTrail,
                                                                       PaymentResultPublisher paymentResultPublisher) {
        return new ProcessInventoryResultService(paymentGateway, transactionRepository, auditTrail,
                paymentResultPublisher);
    }

    @Bean
    public CreatePaymentIntentService createPaymentIntentService(PaymentGateway paymentGateway, AuditTrail auditTrail) {
        return new CreatePaymentIntentService(paymentGateway, auditTrail);
    }

    @Bean
    public VerifyPaymentService verifyPaymentService(PaymentGateway paymentGateway) {
        return new VerifyPaymentService(paymentGateway);
    }

    @Bean
    public TransactionQueryService transactionQueryService(TransactionRepository transactionRepository) {
        return new TransactionQueryService(transactionRepository);
    }
}
