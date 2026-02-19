package com.daniellaera.paymentservice.service.impl;

import com.daniellaera.paymentservice.enums.PaymentStatus;
import com.daniellaera.paymentservice.model.Transaction;
import com.daniellaera.paymentservice.repository.TransactionRepository;
import com.daniellaera.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final TransactionRepository transactionRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Override
    @KafkaListener(topics = "inventory-topic", groupId = "payment-group")
    public void handleInventoryEvent(String message) {
        String[] parts = message.split(":");
        Long orderId = Long.parseLong(parts[0]);
        String inventoryStatus = parts[1];

        Transaction transaction = new Transaction();
        transaction.setOrderId(orderId);

        if ("APPROVED".equals(inventoryStatus)) {
            transaction.setStatus(PaymentStatus.SUCCESS);
            kafkaTemplate.send("payment-topic", orderId + ":SUCCESS");
        } else {
            transaction.setStatus(PaymentStatus.FAILED);
            kafkaTemplate.send("payment-topic", orderId + ":FAILED");
        }

        transactionRepository.save(transaction);
    }

    @DltHandler
    public void handleDlt(String message, Exception ex) {
        log.error("DLT received failed message: {} error: {}", message, ex.getMessage());
    }
}