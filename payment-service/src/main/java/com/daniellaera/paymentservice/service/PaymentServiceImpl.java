package com.daniellaera.paymentservice.service;

import com.daniellaera.paymentservice.dto.InventoryEvent;
import com.daniellaera.paymentservice.dto.PaymentEvent;
import com.daniellaera.paymentservice.dto.TransactionDTO;
import com.daniellaera.paymentservice.enums.PaymentStatus;
import com.daniellaera.paymentservice.exception.ResourceNotFoundException;
import com.daniellaera.paymentservice.model.Transaction;
import com.daniellaera.paymentservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final TransactionRepository transactionRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Override
    @KafkaListener(topics = "inventory-topic", groupId = "payment-group")
    public void handleInventoryEvent(String message) {
        try {
            InventoryEvent event = objectMapper.readValue(message, InventoryEvent.class);

            Transaction transaction = new Transaction();
            transaction.setOrderId(event.orderId());

            if ("APPROVED".equals(event.status())) {
                transaction.setStatus(PaymentStatus.SUCCESS);
                kafkaTemplate.send("payment-topic", objectMapper.writeValueAsString(
                        new PaymentEvent(event.orderId(), event.productName(), event.quantity(), "SUCCESS")
                ));
            } else {
                transaction.setStatus(PaymentStatus.FAILED);
                kafkaTemplate.send("payment-topic", objectMapper.writeValueAsString(
                        new PaymentEvent(event.orderId(), event.productName(), event.quantity(), "FAILED")
                ));
            }
            transactionRepository.save(transaction);
        } catch (Exception e) {
            log.error("Failed to process inventory event: {}", e.getMessage());
        }
    }

    @DltHandler
    public void handleDlt(String message, Exception ex) {
        log.error("DLT received failed message: {} error: {}", message, ex.getMessage());
    }

    @Override
    public List<TransactionDTO> getAllTransactions() {
        return transactionRepository.findAll()
                .stream()
                .map(t -> new TransactionDTO(t.getId(), t.getOrderId(), t.getStatus()))
                .toList();
    }

    @Override
    public TransactionDTO getTransactionById(Long id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + id));
        return new TransactionDTO(transaction.getId(), transaction.getOrderId(), transaction.getStatus());
    }
}