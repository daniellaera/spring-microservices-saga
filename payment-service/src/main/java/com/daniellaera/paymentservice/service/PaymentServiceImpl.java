package com.daniellaera.paymentservice.service;

import com.daniellaera.paymentservice.dto.InventoryResultEvent;
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
    private final StripePaymentService stripePaymentService;

    @Override
    @KafkaListener(topics = "inventory-topic", groupId = "payment-group")
    public void handleInventoryResult(String message) throws Exception {
        InventoryResultEvent event = objectMapper.readValue(message, InventoryResultEvent.class);
        log.info("=== Payment: received inventory result orderId={} status={}",
                event.orderId(), event.status());

        boolean paymentSucceeded = false;

        if ("APPROVED".equals(event.status())) {
            if (event.paymentIntentId() != null && !event.paymentIntentId().isBlank()) {
                paymentSucceeded = stripePaymentService.confirmPayment(event.paymentIntentId());
                log.info("=== Payment: Stripe verification orderId={}: {}",
                        event.orderId(), paymentSucceeded ? "SUCCESS" : "FAILED");
            } else {
                paymentSucceeded = true;
                log.info("=== Payment: no paymentIntentId for orderId {} — auto-approving", event.orderId());
            }
        } else {
            log.warn("=== Payment: skipping payment for orderId={} — inventory REJECTED", event.orderId());
        }

        Transaction transaction = new Transaction();
        transaction.setOrderId(event.orderId());
        transaction.setTotalAmount(event.totalAmount());
        transaction.setStatus(paymentSucceeded ? PaymentStatus.SUCCESS : PaymentStatus.FAILED);
        transactionRepository.save(transaction);

        kafkaTemplate.send("payment-topic", objectMapper.writeValueAsString(
                new PaymentEvent(event.orderId(), event.productName(), event.quantity(),
                        paymentSucceeded ? "SUCCESS" : "FAILED",
                        event.price(), event.totalAmount(), event.userEmail())
        ));
    }

    @DltHandler
    public void handleDlt(String message, Exception ex) {
        log.error("DLT received failed message: {} error: {}", message, ex.getMessage());
    }

    @Override
    public List<TransactionDTO> getAllTransactions() {
        return transactionRepository.findAll()
                .stream()
                .map(t -> new TransactionDTO(t.getId(), t.getOrderId(), t.getTotalAmount(), t.getStatus()))
                .toList();
    }

    @Override
    public TransactionDTO getTransactionById(Long id) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + id));
        return new TransactionDTO(transaction.getId(), transaction.getOrderId(), transaction.getTotalAmount(), transaction.getStatus());
    }
}
