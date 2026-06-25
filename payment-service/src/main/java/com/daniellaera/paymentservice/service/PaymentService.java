package com.daniellaera.paymentservice.service;

import com.daniellaera.paymentservice.dto.TransactionDTO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface PaymentService {
    void handleInventoryResult(String message) throws Exception;

    List<TransactionDTO> getAllTransactions();

    TransactionDTO getTransactionById(Long id);
}