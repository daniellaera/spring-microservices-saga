package com.daniellaera.paymentservice.service;

import com.daniellaera.paymentservice.dto.TransactionDTO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface PaymentService {
    void handleInventoryEvent(String message);

    List<TransactionDTO> getAllTransactions();

    TransactionDTO getTransactionById(Long id);
}